import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

public final class NetworkManager {
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;

    private static HostServer hostServer;
    private static Socket clientSocket;
    private static DataOutputStream clientOutput;
    private static int localSlot;
    private static Consumer<String> clientErrorHandler;

    private NetworkManager() {
    }

    public static synchronized void startServer(int port, String hostName, int maxPlayers,
                                                Consumer<LobbySnapshot> onSnapshot) throws IOException {
        leaveLobby();
        HostServer newServer = new HostServer(port, hostName, maxPlayers, onSnapshot);
        newServer.start();
        hostServer = newServer;
        localSlot = 0;
    }

    public static synchronized boolean connectHost(String ip, int port, String playerName,
                                                   Consumer<LobbySnapshot> onSnapshot,
                                                   Consumer<String> onError) throws IOException {
        leaveLobby();

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);

            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeUTF("JOIN\t" + encode(playerName));
            output.flush();

            String response = input.readUTF();
            if (response.startsWith("ERROR\t")) {
                socket.close();
                return false;
            }
            String[] welcome = response.split("\t", 3);
            if (welcome.length != 3 || !"WELCOME".equals(welcome[0])) {
                throw new IOException("Unexpected response from host.");
            }

            int assignedSlot;
            try {
                assignedSlot = Integer.parseInt(welcome[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid player slot received from host.", e);
            }
            LobbySnapshot initialSnapshot = decodeSnapshot(welcome[2]);
            if (assignedSlot < 1 || assignedSlot >= initialSnapshot.players.length
                    || initialSnapshot.players[assignedSlot] == null) {
                throw new IOException("Host assigned an invalid player slot.");
            }
            socket.setSoTimeout(0);
            clientSocket = socket;
            clientOutput = output;
            clientErrorHandler = onError;
            localSlot = assignedSlot;
            if (onSnapshot != null) {
                onSnapshot.accept(initialSnapshot);
            }

            Thread reader = new Thread(() -> readServerUpdates(socket, input, onSnapshot, onError),
                    "lobby-client-reader");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            if (clientSocket == socket) {
                clientSocket = null;
                clientOutput = null;
                clientErrorHandler = null;
                localSlot = 0;
            }
            throw e;
        }
    }

    public static synchronized void sendTankChange(int tankNum) {
        if (tankNum < 1 || tankNum > 6) {
            throw new IllegalArgumentException("Tank number must be between 1 and 6.");
        }
        if (hostServer != null) {
            hostServer.updateHostTank(tankNum);
        } else if (clientOutput != null) {
            sendClientMessage("TANK\t" + tankNum);
        }
    }

    public static synchronized void leaveLobby() {
        if (hostServer != null) {
            hostServer.stop();
            hostServer = null;
        }
        if (clientSocket != null) {
            try {
                if (clientOutput != null) {
                    clientOutput.writeUTF("LEAVE");
                    clientOutput.flush();
                }
            } catch (IOException ignored) {
                // The peer may already have closed the connection.
            }
            try {
                clientSocket.close();
            } catch (IOException ignored) {
                // Closing a socket during lobby shutdown is best-effort.
            }
            clientSocket = null;
            clientOutput = null;
        }
        localSlot = 0;
        clientErrorHandler = null;
    }

    public static synchronized int getLocalSlot() {
        return localSlot;
    }

    private static synchronized void sendClientMessage(String message) {
        if (clientOutput == null) {
            return;
        }
        try {
            clientOutput.writeUTF(message);
            clientOutput.flush();
        } catch (IOException e) {
            reportError(clientErrorHandler, "Could not send lobby update: " + e.getMessage());
        }
    }

    private static void readServerUpdates(Socket socket, DataInputStream input,
                                          Consumer<LobbySnapshot> onSnapshot,
                                          Consumer<String> onError) {
        try {
            while (!socket.isClosed()) {
                String message = input.readUTF();
                if (message.startsWith("STATE\t")) {
                    if (onSnapshot != null) {
                        onSnapshot.accept(decodeSnapshot(message));
                    }
                } else if (message.startsWith("ERROR\t")) {
                    reportError(onError, decode(message.substring("ERROR\t".length())));
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                reportError(onError, "Connection to the host was lost.");
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // The connection is already unavailable.
            }
            synchronized (NetworkManager.class) {
                if (clientSocket == socket) {
                    clientSocket = null;
                    clientOutput = null;
                    clientErrorHandler = null;
                }
            }
        }
    }

    private static void reportError(Consumer<String> onError, String message) {
        if (onError != null) {
            onError.accept(message);
        }
    }

    static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String value) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid lobby message from host.", e);
        }
    }

    static String encodeSnapshot(LobbySnapshot snapshot) {
        StringBuilder message = new StringBuilder("STATE\t")
                .append(snapshot.maxPlayers).append('\t')
                .append(snapshot.players.length);
        for (PlayerInfo player : snapshot.players) {
            if (player == null) {
                message.append("\t-\t0");
            } else {
                message.append('\t').append(encode(player.name))
                        .append('\t').append(player.tankNum);
            }
        }
        return message.toString();
    }

    static LobbySnapshot decodeSnapshot(String message) throws IOException {
        String[] fields = message.split("\t", -1);
        if (fields.length < 3) {
            throw new IOException("Invalid lobby state received from host.");
        }
        try {
            int maxPlayers = Integer.parseInt(fields[1]);
            int playerCount = Integer.parseInt(fields[2]);
            if (maxPlayers < 2 || maxPlayers > 4 || playerCount != maxPlayers
                    || fields.length != 3 + playerCount * 2) {
                throw new IOException("Invalid lobby state received from host.");
            }
            List<PlayerInfo> players = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                String name = fields[3 + i * 2];
                int tankNum = Integer.parseInt(fields[4 + i * 2]);
                if ("-".equals(name)) {
                    players.add(null);
                } else if (tankNum >= 1 && tankNum <= 6) {
                    players.add(new PlayerInfo(decode(name), tankNum));
                } else {
                    throw new IOException("Invalid tank selection received from host.");
                }
            }
            return new LobbySnapshot(maxPlayers, players.toArray(new PlayerInfo[0]));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid lobby state received from host.", e);
        }
    }

    public static final class PlayerInfo {
        public final String name;
        public final int tankNum;

        public PlayerInfo(String name, int tankNum) {
            this.name = name;
            this.tankNum = tankNum;
        }
    }

    public static final class LobbySnapshot {
        public final int maxPlayers;
        public final PlayerInfo[] players;

        public LobbySnapshot(int maxPlayers, PlayerInfo[] players) {
            this.maxPlayers = maxPlayers;
            this.players = players.clone();
        }

        public int getPlayerCount() {
            int count = 0;
            for (PlayerInfo player : players) {
                if (player != null) {
                    count++;
                }
            }
            return count;
        }
    }
}
