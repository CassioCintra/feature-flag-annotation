package cassio.annotations.messaging.kafka;

public class FeatureFlagKafkaProperties {

    private String topic = "flag.events";
    private String groupId = "feature-flag-consumer";

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
}
