# Tank-Boommm

## Multiplayer lobby

Run `MainMenuUI` on each computer. One player selects **Host Game**; the others
enter that computer's LAN IP address and select **Join Game**. The host listens
on TCP port `8888`. Allow that port through the host computer's firewall when
playing across computers; connecting to `127.0.0.1` only works on the same
computer.

`HostServer.java` owns the server socket and lobby player state.
`NetworkManager.java` provides the client connection and lobby message handling.
The lobby synchronizes player slots and tank selections between the host and
connected clients. The host must remain in the lobby while others are connected.