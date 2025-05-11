class WebSocketService {
  static instance = null;
  socket = null;
  listeners = [];
  isConnected = false;
  
  constructor() {
    if (WebSocketService.instance) {
      return WebSocketService.instance;
    }
    WebSocketService.instance = this;
    this.connect();
  }
  
  connect() {
    if (this.socket && (this.socket.readyState === WebSocket.CONNECTING || this.socket.readyState === WebSocket.OPEN)) {
      return; // Already connected or connecting
    }
    
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    this.socket = new WebSocket(`${protocol}//${host.split(":")[0]}:7070/ws`);
    
    this.socket.onopen = () => {
      console.log('WebSocket connection established');
      this.isConnected = true;
      this.notifyListeners('open', null);
    };

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        this.notifyListeners('message', data);
      } catch (error) {
        console.error('Error parsing WebSocket message:', error);
      }
    };

    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.notifyListeners('error', error);
    };

    this.socket.onclose = () => {
      console.log('WebSocket connection closed');
      this.isConnected = false;
      this.notifyListeners('close', null);
    };
  }
  
  send(message) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      console.error('WebSocket is not connected');
      return false;
    }
    
    if (typeof message === 'object') {
      this.socket.send(JSON.stringify(message));
    } else {
      this.socket.send(message);
    }
    return true;
  }
  
  addListener(listener) {
    this.listeners.push(listener);
    // If already connected, notify the new listener
    if (this.isConnected) {
      listener('open', null);
    }
  }
  
  removeListener(listener) {
    const index = this.listeners.indexOf(listener);
    if (index !== -1) {
      this.listeners.splice(index, 1);
    }
  }
  
  notifyListeners(event, data) {
    this.listeners.forEach(listener => {
      listener(event, data);
    });
  }
  
  close() {
    if (this.socket) {
      this.socket.close();
    }
  }
  
  // Singleton instance getter
  static getInstance() {
    if (!WebSocketService.instance) {
      new WebSocketService();
    }
    return WebSocketService.instance;
  }

  // Check connection status
  isSocketConnected() {
    return this.isConnected;
  }
}

export default WebSocketService;
