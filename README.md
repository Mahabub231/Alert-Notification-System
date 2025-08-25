# Alert & Notification System

A real-time client-server messaging application built with Java that enables efficient alert management and communication between multiple clients and a central server.

## 🚀 Features

### Client Side
- Connect/disconnect from server
- Auto-generated unique client names
- Send messages to server
- Receive server replies with popup notifications

### Server Side
- Start/stop server on configurable port
- Real-time alert management with read/unread status
- Visual and auditory notifications for new messages
- Direct reply functionality to clients
- Multi-client support with thread pooling

## 🛠️ Technology Stack

- **Language**: Java SE
- **UI Framework**: Java Swing
- **Networking**: Socket programming
- **Concurrency**: ExecutorService for multi-threading
- **Storage**: File-based persistence for client counters

## 📦 Project Structure

```
src/
├── ClientApp.java          # Main client application
├── ClientFrame.java        # Client GUI implementation
├── ServerApp.java          # Main server application
├── ServerFrame.java        # Server GUI implementation
├── ClientHandler.java      # Handles individual client connections
├── AlertItem.java          # Alert data model
└── AlertCellRenderer.java  # Custom alert list rendering
```

## 🎯 How to Run

1. **Start the Server**:
   ```bash
   java ServerApp
   ```

2. **Start Clients**:
   ```bash
   java ClientApp
   ```

3. **Configure Connection**:
   - Default server: localhost:5050
   - Clients auto-connect with unique names

## ✨ Key Functionality

- Real-time bidirectional communication
- Persistent client numbering across sessions
- Visual indicators for unread messages
- Customizable notification preferences (beep/popup)
- Thread-safe client management

## 🔮 Future Enhancements

- Database integration for message persistence
- Client authentication system
- Message encryption for security
- Enhanced GUI with JavaFX
- Email/SMS notification support

This project demonstrates core Java networking concepts, Swing GUI development, and multi-threaded server architecture suitable for academic projects or small-scale alert systems.
