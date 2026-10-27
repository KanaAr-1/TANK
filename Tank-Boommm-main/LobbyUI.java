import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

public class LobbyUI extends JFrame {

    private int selectedTankIndex = 0; // Index รถถังที่เราเลือก (0 ถึง 5)
    private int maxPlayers = 4;

    // ข้อมูลผู้เล่น 4 ช่อง (สล็อตแรกเป็นของเรา สล็อตอื่นว่างไว้รอเพื่อนจอย)
    private String[] playerNames = {null, null, null, null};
    private int[] playerTanks = {1, 0, 0, 0};

    // UI Components
    private JLabel lblStatus;
    private JLabel lblCountdown;
    private JLabel[] playerTankLabels = new JLabel[4];
    private JLabel[] playerNameLabels = new JLabel[4];
    private final LobbyController lobbyController;

    private final int[][] tankBtnPos = {
        {120, 185}, {204, 186},
        {120, 269}, {204, 269},
        {120, 351}, {204, 351}
    };

    private final int[][] playerCardPos = {
        {290, 184}, {492, 184},
        {290, 309}, {492, 309}
    };

    public LobbyUI(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        this.lobbyController = new LobbyController(this);
        setTitle("Tank Boommm - Lobby");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                NetworkManager.leaveLobby();
                
            }
        });

        BackgroundPanel mainPanel = new BackgroundPanel("image/lobby_bg.jpg");
        mainPanel.setLayout(null);

        // 1. Status ด้านบน
        lblStatus = new JLabel("Status: Waiting for players...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Monospaced", Font.BOLD, 15));
        lblStatus.setForeground(Color.BLACK);
        lblStatus.setBounds(295, 122, 382, 28);
        mainPanel.add(lblStatus);

        // 2. ปุ่มเลือกรถถัง 6 ช่อง (เปลี่ยนรูปรถถังเราทันที)
        for (int i = 0; i < 6; i++) {
            int tankNum = i + 1;
            int index = i;

            JButton btn = new JButton(loadIcon("image/Tank" + tankNum + ".png", 38, 38));
            btn.setBounds(tankBtnPos[i][0], tankBtnPos[i][1], 48, 48);
            styleButton(btn);

            btn.addActionListener(e -> {
                selectedTankIndex = index;
                int myTankNum = selectedTankIndex + 1;
                int localSlot = NetworkManager.getLocalSlot();

                // เปลี่ยนรูปรถถังในการ์ดของเรา (Slot 0) ทันที
                if (playerNames[localSlot] != null) {
                    updateSlot(localSlot, playerNames[localSlot], myTankNum);
                }
                mainPanel.repaint(); // วาดขอบส้มไฮไลต์ใหม่
                NetworkManager.sendTankChange(myTankNum);
            });

            mainPanel.add(btn);
        }

        // 3. การ์ดผู้เล่น 4 ช่อง
        for (int i = 0; i < 4; i++) {
            int x = playerCardPos[i][0];
            int y = playerCardPos[i][1];

            playerTankLabels[i] = new JLabel("", SwingConstants.CENTER);
            playerTankLabels[i].setBounds(x + 10, y + 5, 162, 75);

            playerNameLabels[i] = new JLabel("", SwingConstants.CENTER);
            playerNameLabels[i].setFont(new Font("Monospaced", Font.BOLD, 13));
            playerNameLabels[i].setForeground(Color.BLACK);
            playerNameLabels[i].setBounds(x + 12, y + 70, 162, 22);

            mainPanel.add(playerTankLabels[i]);
            mainPanel.add(playerNameLabels[i]);
        }

        refreshDisplay();

        JButton btnStart = new JButton(loadIcon("image/start_btn.png",200,155));
        btnStart.setBounds(120, 422, 155,50);
        styleButton(btnStart);
        btnStart.addActionListener(e -> {
            if (NetworkManager.isHost()) {
                lobbyController.startCountdown(3);
                NetworkManager.sendCountdownStart(3);
            }
        });
        mainPanel.add(btnStart);
        // 4. ปุ่ม Leave Lobby
        JButton btnLeave = new JButton(loadIcon("image/leave_btn.png", 200, 155));
        btnLeave.setBounds(265, 370, 200, 155);
        styleButton(btnLeave);
        btnLeave.addActionListener(e -> {
            NetworkManager.leaveLobby();
            this.dispose();
            new MainMenuUI().setVisible(true);
        });
        mainPanel.add(btnLeave);

        // 5. Countdown ด้านล่าง
        lblCountdown = new JLabel("Waiting...", SwingConstants.CENTER);
        lblCountdown.setFont(new Font("Monospaced", Font.BOLD, 15));
        lblCountdown.setForeground(Color.BLACK);
        lblCountdown.setBounds(452, 425, 220, 40);
        mainPanel.add(lblCountdown);

        add(mainPanel);
    }

    // ฟังก์ชันสำหรับอัปเดตการ์ดผู้เล่นในสล็อต (slotIndex: 0-3)
    public void updateSlot(int slotIndex, String name, int tankNum) {
        if (slotIndex >= 0 && slotIndex < 4) {
            playerNames[slotIndex] = name;
            playerTanks[slotIndex] = tankNum;
            refreshDisplay();
        }
    }

    public void applySnapshot(NetworkManager.LobbySnapshot snapshot) {
        Runnable update = () -> {
            for (int i = 0; i < playerNames.length; i++) {
                NetworkManager.PlayerInfo player = i < snapshot.players.length ? snapshot.players[i] : null;
                updateSlot(i, player == null ? null : player.name, player == null ? 1 : player.tankNum);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    public void showNetworkError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this, message, "Network error", JOptionPane.ERROR_MESSAGE));
    }

    private void refreshDisplay() {
        for (int i = 0; i < 4; i++) {
            if (i >= maxPlayers) {
                playerTankLabels[i].setIcon(null);
                playerNameLabels[i].setText("[ N/A ]");
                playerNameLabels[i].setForeground(Color.RED);
            } else {
                playerNameLabels[i].setForeground(Color.BLACK);
                if (playerNames[i] != null) {
                    playerTankLabels[i].setIcon(loadIcon("image/Tank" + playerTanks[i] + ".png", 55, 55));
                    playerNameLabels[i].setText(playerNames[i]);
                } else {
                    playerTankLabels[i].setIcon(null);
                    playerNameLabels[i].setText("Waiting...");
                }
            }
        }
    }

    public JLabel getLblStatus() { return lblStatus; }
    public JLabel getLblCountdown() { return lblCountdown; }
    public LobbyController getLobbyController() { return lobbyController; }

    private ImageIcon loadIcon(String path, int w, int h) {
        try {
            Image img = new ImageIcon(path).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(img);
        } catch (Exception e) { return null; }
    }

    private void styleButton(JButton btn) {
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
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

            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(new Color(230, 160, 40));
            g2d.setStroke(new BasicStroke(3));
            int x = tankBtnPos[selectedTankIndex][0];
            int y = tankBtnPos[selectedTankIndex][1];
            g2d.drawRect(x - 2, y - 2, 52, 52);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LobbyUI(4).setVisible(true));
    }
}