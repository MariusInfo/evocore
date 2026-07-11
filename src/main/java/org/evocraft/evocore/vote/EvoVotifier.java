package org.evocraft.evocore.vote;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.crypto.Cipher;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class EvoVotifier {

    private static final int PORT = 8192; // Rămâne pe 8192 cum ai setat în Pterodactyl!
    private static PrivateKey privateKey;
    private static ServerSocket serverSocket;
    private static boolean isRunning = false;

    public static void start() {
        if (isRunning) return;

        try {
            loadOrGenerateKeys();

            serverSocket = new ServerSocket(PORT);
            isRunning = true;

            System.out.println("[EvoVotifier] Vote system started on port " + PORT + "!");

            // Thread-ul principal care doar ACCEPTĂ conexiuni
            new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setSoTimeout(5000); // 5 secunde timeout pentru fiecare conexiune

                        // ========================================================
                        // FIX-UL AICI: Procesăm fiecare vot pe un Thread NOU!
                        // Astfel, dacă un bot scanează portul, nu blochează voturile reale!
                        // ========================================================
                        new Thread(() -> {
                            try {
                                handleVoteConnection(socket);
                            } catch (Exception e) {
                                // Ignorăm erorile de tip "timeout" de la scannere de internet
                            }
                        }).start();

                    } catch (Exception e) {
                        if (isRunning && !serverSocket.isClosed()) {
                            System.err.println("[EvoVotifier] Error while accepting connection: " + e.getMessage());
                        }
                    }
                }
            }, "EvoVotifier-Main-Thread").start();

        } catch (Exception e) {
            System.err.println("[EvoVotifier] CRITICAL ERROR: Could not start vote system!");
            e.printStackTrace();
        }
    }

    public static void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleVoteConnection(Socket socket) throws Exception {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write("VOTIFIER 1.9\n");
            writer.flush();

            InputStream in = socket.getInputStream();
            byte[] block = new byte[256];
            int read = 0;
            while (read < 256) {
                int r = in.read(block, read, 256 - read);
                if (r == -1) break;
                read += r;
            }

            if (read == 256) {
                Cipher cipher = Cipher.getInstance("RSA");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                String data = new String(cipher.doFinal(block));

                String[] parts = data.split("\n");
                if (parts.length >= 5 && parts[0].equals("VOTE")) {
                    String username = parts[2];

                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        server.execute(() -> {
                            System.out.println("[EvoVotifier] Received a valid vote for: " + username);
                            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "adminvote " + username);
                        });
                    }
                }
            }
        } finally {
            socket.close();
        }
    }

    private static void loadOrGenerateKeys() throws Exception {
        File dir = FMLPaths.CONFIGDIR.get().resolve("evocore/votifier").toFile();
        if (!dir.exists()) dir.mkdirs();

        File pubFile = new File(dir, "public.key");
        File privFile = new File(dir, "private.key");

        if (!pubFile.exists() || !privFile.exists()) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();

            Files.writeString(pubFile.toPath(), Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            Files.writeString(privFile.toPath(), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            privateKey = pair.getPrivate();
        } else {
            byte[] privBytes = Base64.getDecoder().decode(Files.readString(privFile.toPath()).trim());
            PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            privateKey = kf.generatePrivate(privSpec);
        }
    }
}
