import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;

public class MainMenuUI extends JFrame {

    private static final int LOBBY_PORT = 8888;
    private JTextField txtName;
    private JTextField txtIP;
    private int maxPlayers = 3;

    private final int dotX = 341;
    private final int dotY3 = 296;
    private final int dotY4 = 327;

    public MainMenuUI() {
        setTitle("Tank Boommm - Main Menu");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        BackgroundPanel mainPanel = new BackgroundPanel("image/connection_bg.jpg");
        mainPanel.setLayout(null);

        txtName = new JTextField("Player_1");
        txtName.setBounds(345, 188, 280, 26);
        styleTextField(txtName);

        txtIP = new JTextField(getMyLocalIP());
        txtIP.setBounds(345, 237, 280, 26);
        styleTextField(txtIP);

        // --- ปุ่ม HOST GAME ---
        JButton btnHost = new JButton(loadIcon("image/host_btn.png", 360, 180));
        btnHost.setBounds(60, 380, 360, 180);
        styleButton(btnHost);
        btnHost.addActionListener(e -> {
            String playerName = txtName.getText().trim();
            if (playerName.isEmpty()) {
                showInputError("Please enter a player name.");
                return;
            }
            LobbyUI lobby = new LobbyUI();
            LobbyController controller = new LobbyController(lobby);
            try {
                NetworkManager.startServer(LOBBY_PORT, playerName + " (Host)", maxPlayers, snapshot -> {
                    SwingUtilities.invokeLater(() -> {
                        lobby.applySnapshot(snapshot);
                        controller.updatePlayerStatus(snapshot.getPlayerCount(), snapshot.maxPlayers);
                    });
                });
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not start the host: " + ex.getMessage(),
                        "Host error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            this.dispose();
            lobby.setVisible(true);
        });

        // --- ปุ่ม JOIN GAME ---
        JButton btnJoin = new JButton(loadIcon("image/join_btn.png", 360, 180));
        btnJoin.setBounds(370, 380, 360, 180);
        styleButton(btnJoin);
        btnJoin.addActionListener(e -> {
            String targetIP = txtIP.getText().trim();
            String playerName = txtName.getText().trim();
            if (playerName.isEmpty() || targetIP.isEmpty()) {
                showInputError("Enter both a player name and the host IP address.");
                return;
            }
            LobbyUI lobby = new LobbyUI();
            LobbyController controller = new LobbyController(lobby);
            try {
                boolean connected = NetworkManager.connectHost(targetIP, LOBBY_PORT, playerName,
                        snapshot -> {
                            SwingUtilities.invokeLater(() -> {
                                lobby.applySnapshot(snapshot);
                                controller.updatePlayerStatus(snapshot.getPlayerCount(), snapshot.maxPlayers);
                            });
                        }, lobby::showNetworkError);
                if (!connected) {
                    JOptionPane.showMessageDialog(this, "Cannot join the lobby. It may be full.",
                            "Connection error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Cannot connect to host: " + ex.getMessage(),
                        "Connection error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            this.dispose();
            lobby.setVisible(true);
        });

        // เลือก Max Players
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int mx = e.getX(), my = e.getY();
                if (Math.abs(mx - dotX) < 20 && Math.abs(my - dotY3) < 20) {
                    maxPlayers = 3;
                    mainPanel.repaint();
                } else if (Math.abs(mx - dotX) < 20 && Math.abs(my - dotY4) < 20) {
                    maxPlayers = 4;
                    mainPanel.repaint();
                }
            }
        });

        mainPanel.add(txtName);
        mainPanel.add(txtIP);
        mainPanel.add(btnHost);
        mainPanel.add(btnJoin);

        add(mainPanel);
    }

    private ImageIcon loadIcon(String path, int w, int h) {
        try {
            Image img = new ImageIcon(path).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        } catch (Exception e) { return null; }
    }

    private void styleTextField(JTextField tf) {
        tf.setFont(new Font("Monospaced", Font.BOLD, 16));
        tf.setForeground(new Color(255, 220, 100));
        tf.setCaretColor(Color.YELLOW);
        tf.setOpaque(false);
        tf.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
    }

    private void styleButton(JButton btn) {
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private String getMyLocalIP() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private void showInputError(String message) {
        JOptionPane.showMessageDialog(this, message, "Invalid input", JOptionPane.ERROR_MESSAGE);
    }

    class BackgroundPanel extends JPanel {
        private Image bgImage;

        public BackgroundPanel(String path) {
            bgImage = new ImageIcon(path).getImage();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (bgImage != null) g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);

            g.setColor(new Color(45, 40, 30));
            if (maxPlayers == 3) {
                g.fillOval(dotX - 10, dotY3 - 10, 20, 20);
            } else if (maxPlayers == 4) {
                g.fillOval(dotX - 10, dotY4 - 10, 20, 20);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainMenuUI().setVisible(true));
    }
}