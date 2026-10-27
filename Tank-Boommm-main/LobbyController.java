import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class LobbyController {

    private LobbyUI lobbyUI;
    private Timer countdownTimer;

    public LobbyController(LobbyUI lobbyUI) {
        this.lobbyUI = lobbyUI;
    }

    public void updatePlayerStatus(int currentPlayers, int maxPlayers) {
        lobbyUI.getLblStatus().setText("Status: Waiting for players (" + currentPlayers + "/" + maxPlayers + ")...");

       
    }

    

    public void startCountdown(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }

        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }

        final int[] count = {seconds};
        countdownTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (count[0] > 0) {
                    lobbyUI.getLblCountdown().setText("Starting in " + count[0] + "...");
                    count[0]--;
                } else {
                    ((Timer) e.getSource()).stop();
                    lobbyUI.getLblCountdown().setText("Game Starting!");

                    // TODO [ทีม Gameplay]: สลับเปิดหน้า GameUI
                    // lobbyUI.dispose();
                    // new GameUI().setVisible(true);
                }
            }
        });
        countdownTimer.setInitialDelay(0);
        countdownTimer.start();
        if (seconds > 0) {
            lobbyUI.getLblCountdown().setText("Starting in " + seconds + "...");
        } else {
            lobbyUI.getLblCountdown().setText("Game Starting!");
        }
    }

    // ตัวอย่างแนวคิดฟังก์ชันจัดการเมื่อมีคนออกห้อง
        public void handlePlayerLeave(int remainingPlayers, int maxPlayers) {
         // 1. ถ้า Timer นับถอยหลังค้างอยู่ ให้หยุดทันทีเพราะคนไม่ครบแล้ว
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
             lobbyUI.getLblCountdown().setText("Player left! Waiting...");
        }
    
            // 2. อัปเดตสถานะจำนวนคนใหม่
             updatePlayerStatus(remainingPlayers, maxPlayers);
        }
}