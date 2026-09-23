package edu.vku.lancord.common.protocol;

public enum MessageType {
    LOGIN,
    LOGIN_RESP,
    REGISTER,
    REGISTER_RESP,
    ONLINE_USERS_UPDATE,
    CREATE_GROUP,
    CREATE_GROUP_RESP,
    GROUP_INVITE,
    GROUP_INVITE_RESP,
    CREATE_CHANNEL,
    CREATE_CHANNEL_RESP,
    SEND_DM,
    SEND_GROUP_MSG,
    NEW_MESSAGE_NOTIFY,
    UPLOAD_FILE_INIT,
    UPLOAD_FILE_RESP,
    FILE_CHUNK,
    FILE_UPLOAD_COMPLETE,
    DOWNLOAD_FILE_REQ,
    DOWNLOAD_FILE_RESP,
    STREAM_START,
    STREAM_STARTED, // Contains multicast IP and port
    ERROR,
    GET_CHAT_HISTORY,
    CHAT_HISTORY_RESP,
    FILE_UPLOAD_NOTIFY,        // Server broadcasts to others when a file upload completes
    GET_FILES_IN_CONTEXT,      // Client requests list of files in a DM or Group
    FILES_IN_CONTEXT_RESP,     // Server responds with list of FileMetadata
    CALL_REQUEST,              // Caller -> Server
    CALL_INCOMING,             // Server -> Receiver
    CALL_ACCEPT,               // Receiver -> Server
    CALL_REJECT,               // Receiver -> Server
    CALL_REJECTED,             // Server -> Caller
    STREAM_STOP,               // Client -> Server
    STREAM_STOPPED             // Server -> Clients
}
