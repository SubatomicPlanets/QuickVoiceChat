# QuickVoiceChat

## About
QuickVoiceChat is a simple, minimal, and lightweight Minecraft server plugin for Paper, which allows players to connect to a web-based proximity voice chat. It focuses on privacy and security so that it works well on private servers that use a whitelist. It was made for a single use case, so it may be missing some features, and it isn't very customizable yet, but at least it works (probably).

## How it works
When a player joins (or when they type /vc), they can click a text in the Minecraft chat and are linked to a website. The web server is hosted by this plugin and automatically uses web sockets and WebRTC to open voice chat connections and adjust player volumes.

## How to set it up
To use QuickVoiceChat on your server, simply download the newest QuickVoiceChat.jar and put it in the plugins folder. You can directly create the config file (QuickVoiceChat/config.yml) or let the plugin generate it by restarting your server. HTTPS is used when the plugin finds the `key.pem` and `cert.pem` files. The default location for these files is in the plugin data folder, but this can be changed in the config file. If these files are missing, it will fall back to HTTP. The recommended way to use this plugin is to get a domain name for your server and get a certificate from services like Let's Encrypt. This is because HTTP has many limits in most browsers. Self-signed certificates might show a warning when visiting the site and may also have restrictions.
Don't forget to forward the port you use for the web server!

## Configuration
QuickVoiceChat uses a YAML config file, which is generated on the first run automatically. The following properties are customizable:
- `webserver.address`: The address for the web server. Most of the time this will be your server's domain name (e.g. "myserver.net")
- `webserver.port`: The port used by the web server. You can use the standard HTTPS port (443) or a custom port.
- `chat.auto-send`: If true, automatically sends a player a voice chat link when they join the server
- `chat.connection-messages`: If true, sends notification messages in the chat when the voice chat connects or disconnects for some reason
- `voice.falloff-type`: The type of falloff used by the voice chat. Currently supported are "physics" for a realistic falloff, "game" for a slightly realistic but less intense falloff, and "linear" for a simple linear falloff.
- `voice.min-distance`: The minimum distance used by the falloff.
- `voice.max-distance`: The maximum distance used by the falloff.
- `cert-pem-path`: The path to your certificate file in PEM format
- `key-pem-path`: The path to your key file in PEM format

## Security and privacy
QuickVoiceChat is perfect for small, self-hosted, private servers. The web server tokens are securely managed, and the web server only allows connections from the set of IP addresses that have recently requested a voice chat link. The web server uses HTTPS, the web sockets use WSS, and the actual voice data is end-to-end encrypted using the standard WebRTC protocol. Players can only connect to the voice chat if they are currently on the Minecraft server, and they automatically get disconnected when they disconnect in Minecraft. Releases are built using GitHub Actions and artifact attestations. The only dependencies used are Paper, JUnit (for tests), and Netty (for the web server), all of which are incredibly well known.
> [!NOTE]
> Because HTTP/HTTPS servers use TCP, the initial connection cannot be blocked on the application level. This means network scanners can still see that a server is running at IP:WEBSERVER-PORT. This could be fixed by changing firewall rules automatically but I have not found a way to do this in a nice and simple cross-platform and docker-friendly way. It is recommended to use a not-well-known port for the web server as that makes it harder to find.

## TODO
- Support 3D audio, currently only volume is controlled by player distance
- Improve the HTTPS workflow (more secure ways of storing the required PEM files, and maybe removing the HTTP fallback)
- Improve token timeouts, maybe set it very high since it already filters using IP, or show a page saying the token is no longer valid
- Support muting and unmuting with a command in Minecraft and a button on the website and sync them
- Add more configuration options
- Support custom HTML, JS, and CSS files
- Maybe support external web servers
- Maybe find a way to change firewall rules that doesn't require too much user setup for a fully hidden web server
- Add more tests