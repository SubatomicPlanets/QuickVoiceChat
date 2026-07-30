const token = new URLSearchParams(window.location.search).get('t');
const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
const wsUrl = `${wsProtocol}://${window.location.host}/vcws?t=${token}`;

const config = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' }
    ]
};

const peers = new Map();
const audioElements = new Map();
const targetVolumes = new Map();
let ws = null, localStream = null, silentStream = null, requestingStream = false;

function startSilentStream() {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    gain.gain.value = 0;
    const dst = ctx.createMediaStreamDestination();
    oscillator.connect(gain);
    gain.connect(dst);
    oscillator.start();
    silentStream = dst.stream;
}

async function initWebSocket() {
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('WebSocket connected');
        startLocalStream();
    };

    ws.onmessage = async (event) => {
        const data = event.data;
        if (!data) return;

        const split = data.split(':');
        const parts = [...split.slice(0, 2), split.slice(2).join(':')];
        const cmd = parts[0];

        if (cmd === 'j' && parts[1]) {
            console.log('Join command received');
            createPeer(parts[1], true);
        } else if (cmd === 'l' && parts[1]) {
            console.log('Leave command received');
            closePeer(parts[1]);
        } else if (cmd === 'o' && parts[2]) {
            console.log('WebRTC offer received');
            handleOffer(parts[1], parts[2]);
        } else if (cmd === 'a' && parts[2]) {
            console.log('WebRTC answer received');
            handleAnswer(parts[1], parts[2]);
        } else if (cmd === 'c' && parts[2]) {
            try {
                handleCandidate(parts[1], JSON.parse(parts[2]));
            } catch (e) { }
        } else if (cmd === 'v' && parts[2]) {
            const volume = parseFloat(parts[2]);
            if (!isNaN(volume) && volume >= 0.0 && volume <= 1.0) {
                targetVolumes.set(parts[1], volume);
            }
        }
    };

    ws.onclose = () => {
        console.log('WebSocket closed');
        cleanup();
    }

    ws.onerror = (e) => console.error('WebSocket error:', e);
}

async function startLocalStream() {
    if (localStream || requestingStream) return;

    try {
        requestingStream = true
        localStream = await navigator.mediaDevices.getUserMedia({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true,
                sampleRate: 48000,
                channelCount: 1
            }
        });
    } catch (e) {
        console.error('Microphone error:', e);
    }
    finally {
        requestingStream = false
    }

    if (localStream) {
        const audioTrack = localStream.getAudioTracks()[0];
        peers.forEach((pc, _) => {
            const audioTransceiver = pc.getTransceivers()[0];
            if (audioTransceiver && audioTransceiver.sender) {
                audioTransceiver.sender.replaceTrack(audioTrack);
                audioTransceiver.sender.setStreams(localStream);
            }
        });
    }
}

async function createPeer(peerId, isInitiator) {
    if (peers.has(peerId)) return null;

    const pc = new RTCPeerConnection(config);
    peers.set(peerId, pc);

    const audio = document.createElement('audio');
    audio.volume = 0.0;
    audio.autoplay = true;
    audio.playsInline = true;
    document.body.appendChild(audio);
    audioElements.set(peerId, audio);

    pc.ontrack = e => {
        if (e.streams && e.streams[0]) {
            audio.srcObject = e.streams[0];
        }
    };

    pc.onicecandidate = e => {
        if (e.candidate && ws.readyState === WebSocket.OPEN) {
            ws.send(`c:${peerId}:${JSON.stringify(e.candidate)}`);
        }
    };

    if (isInitiator) {
        if (localStream) {
            const audioTrack = localStream.getAudioTracks()[0];
            pc.addTransceiver(audioTrack, {
                direction: 'sendrecv',
                streams: [localStream]
            });
        } else {
            const audioTrack = silentStream.getAudioTracks()[0];
            pc.addTransceiver(audioTrack, {
                direction: 'sendrecv',
                streams: [silentStream]
            });
            startLocalStream();
        }
        console.log('Creating WebRTC offer');
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        ws.send(`o:${peerId}:${offer.sdp}`);
    }

    return pc;
}

async function handleOffer(from, sdp) {
    let pc = peers.get(from);
    if (!pc) pc = await createPeer(from, false);

    if (pc) {
        await pc.setRemoteDescription({ type: 'offer', sdp });

        const audioTransceiver = pc.getTransceivers()[0];
        if (audioTransceiver) {
            if (localStream) {
                const audioTrack = localStream.getAudioTracks()[0];
                audioTransceiver.sender.replaceTrack(audioTrack);
                audioTransceiver.sender.setStreams(localStream);
            } else {
                const audioTrack = silentStream.getAudioTracks()[0];
                audioTransceiver.sender.replaceTrack(audioTrack);
                audioTransceiver.sender.setStreams(silentStream);
                startLocalStream();
            }
            audioTransceiver.direction = 'sendrecv';
        }

        console.log('Creating WebRTC answer');
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        ws.send(`a:${from}:${answer.sdp}`);
    }
}

async function handleAnswer(from, sdp) {
    const pc = peers.get(from);
    if (pc) await pc.setRemoteDescription({ type: 'answer', sdp });
}

async function handleCandidate(from, candidate) {
    const pc = peers.get(from);
    if (pc) await pc.addIceCandidate(candidate);
}

function closePeer(peerId) {
    const pc = peers.get(peerId);
    if (pc) pc.close();
    peers.delete(peerId);

    const audio = audioElements.get(peerId);
    if (audio) {
        audio.srcObject = null;
        audio.remove();
    }
    audioElements.delete(peerId);
}

function cleanup() {
    for (let peerId of peers.keys()) {
        closePeer(peerId);
    }
    if (localStream) {
        localStream.getTracks().forEach(track => track.stop());
        localStream = null;
    }
}

function smoothVolumeLoop() {
    for (const [id, target] of targetVolumes) {
        const el = audioElements.get(id);
        if (!el) {
            targetVolumes.delete(id);
            continue;
        }

        const nextVolume = Math.max(0, Math.min(1, el.volume + (target - el.volume) * 0.1));

        if (Math.abs(nextVolume - target) < 0.001) {
            el.volume = target;
        } else {
            el.volume = nextVolume;
        }
    }

    requestAnimationFrame(smoothVolumeLoop);
}

function checkAndPlayAllStreams() {
    audioElements.forEach((audio, _) => {
        if (audio.srcObject && audio.paused) {
            audio.play();
        }
    });
}

window.addEventListener('beforeunload', () => {
    cleanup();
    if (ws) ws.close();
});

startSilentStream();
initWebSocket();
setInterval(checkAndPlayAllStreams, 2000);
requestAnimationFrame(smoothVolumeLoop);