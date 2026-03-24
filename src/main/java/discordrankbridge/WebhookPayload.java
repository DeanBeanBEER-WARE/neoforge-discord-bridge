package discordrankbridge;

public class WebhookPayload {
    public String action;
    public String rank;
    public String discordUserId;
    public String discordUsername;
    public String minecraftName;
    public String message;
    public String timestamp;
    public String code;
    public String discordId;
    public String reason;
    public String sender;
    public String sortBy;
    public String command;
    public Boolean mode;
    public Integer duration;
    public java.util.List<String> allowedPlayers;

    @Override
    public String toString() {
        return "WebhookPayload{" +
                "action='" + action + '\'' +
                "command='" + command + '\'' +
                ", rank='" + rank + '\'' +
                ", discordUserId='" + discordUserId + '\'' +
                ", discordUsername='" + discordUsername + '\'' +
                ", minecraftName='" + minecraftName + '\'' +
                ", message='" + message + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", code='" + code + '\'' +
                ", discordId='" + discordId + '\'' +
                ", reason='" + reason + '\'' +
                ", sender='" + sender + '\'' +
                '}';
    }
}
