package com.ullink.slack.simpleslackapi.impl;

import com.ullink.slack.simpleslackapi.ChannelHistoryModule;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.ReactionAdded;
import com.ullink.slack.simpleslackapi.events.ReactionRemoved;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.events.SlackReplyEvent;
import com.ullink.slack.simpleslackapi.listeners.ReactionAddedListener;
import com.ullink.slack.simpleslackapi.listeners.ReactionRemovedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ChannelHistoryModuleImpl implements ChannelHistoryModule {

    private final SlackSession session;
    private static final String FETCH_CHANNEL_HISTORY_COMMAND = "channels.history";

    public ChannelHistoryModuleImpl(SlackSession session) {
        this.session = session;
    }

    @Override
    public List<SlackMessagePosted> fetchHistoryOfChannel(String channelId) {
        return fetchHistoryOfChannel(channelId, null, -1);
    }

    @Override
    public List<SlackMessagePosted> fetchHistoryOfChannel(String channelId, LocalDate day) {
        return fetchHistoryOfChannel(channelId, day, -1);
    }

    @Override
    public List<SlackMessagePosted> fetchHistoryOfChannel(String channelId, int numberOfMessages) {
        return fetchHistoryOfChannel(channelId, null, numberOfMessages);
    }

    @Override
    public List<SlackMessagePosted> fetchHistoryOfChannel(String channelId, LocalDate day, int numberOfMessages) {
        Map<String, String> params = new HashMap<>();
        params.put("channel", channelId);
        if (day != null) {
            Date start = Date.from(day.atStartOfDay().toInstant(ZoneOffset.UTC));
            Date end = Date.from(day.atTime(23, 59).toInstant(ZoneOffset.UTC));
            params.put("oldest", convertDateToSlackTimestamp(start));
            params.put("latest", convertDateToSlackTimestamp(end));
        }
        if (numberOfMessages > -1) {
            params.put("count", String.valueOf(numberOfMessages));
        } else {
            params.put("count", String.valueOf(1000));
        }
        return fetchHistoryOfChannel(params);
    }

    private List<SlackMessagePosted> fetchHistoryOfChannel(Map<String, String> params) {
        SlackMessageHandleImpl handle = new SlackMessageHandleImpl(session.getNextMessageId());
        session.postSlackCommand(params, FETCH_CHANNEL_HISTORY_COMMAND, handle);
        SlackReplyEvent replyEv = handle.getSlackReply();
        JSONObject answer = replyEv.getPlainAnswer();
        JSONArray events = (JSONArray) answer.get("messages");
        List<SlackMessagePosted> messages = new ArrayList<>();
        if (events != null) {
            for (Object event : events) {
                if ((((JSONObject) event).get("subtype") == null)) {
                    messages.add((SlackMessagePosted) SlackJSONMessageParser.decode(session, (JSONObject) event));
                }
            }
        }
        return messages;
    }

    @Override
    public List<SlackMessagePosted> fetchUpdatingHistoryOfChannel(String channelId) {
        return fetchUpdatingHistoryOfChannel(channelId, null, -1);
    }

    @Override
    public List<SlackMessagePosted> fetchUpdatingHistoryOfChannel(String channelId, LocalDate day) {
        return fetchUpdatingHistoryOfChannel(channelId, day, -1);
    }

    @Override
    public List<SlackMessagePosted> fetchUpdatingHistoryOfChannel(String channelId, int numberOfMessages) {
        return fetchUpdatingHistoryOfChannel(channelId, null, numberOfMessages);
    }

    @Override
    public List<SlackMessagePosted> fetchUpdatingHistoryOfChannel(String channelId, LocalDate day, int numberOfMessages) {
        List<SlackMessagePosted> messages = fetchHistoryOfChannel(channelId, day, numberOfMessages);
        session.addReactionAddedListener(new ChannelHistoryReactionAddedListener(messages));
        session.addReactionRemovedListener(new ChannelHistoryReactionRemovedListener(messages));
        session.addMessagePostedListener(new ChannelHistoryMessagePostedListener(messages));
        return messages;
    }

    public class ChannelHistoryReactionAddedListener implements ReactionAddedListener {

        List<SlackMessagePosted> messages = new ArrayList<>();

        public ChannelHistoryReactionAddedListener(List<SlackMessagePosted> initialMessages) {
            messages = initialMessages;
        }

        @Override
        public void onEvent(ReactionAdded event, SlackSession session) {
            String emojiName = event.getEmojiName();
            for (SlackMessagePosted message : messages) {
                for (String reaction : message.getReactions().keySet()) {
                    if (emojiName.equals(reaction)) {
                        int count = message.getReactions().get(emojiName);
                        message.getReactions().put(emojiName, count++);
                        return;
                    }
                }
                message.getReactions().put(emojiName, 1);
            }
        }
    };

    public class ChannelHistoryReactionRemovedListener implements ReactionRemovedListener {

        List<SlackMessagePosted> messages = new ArrayList<>();

        public ChannelHistoryReactionRemovedListener(List<SlackMessagePosted> initialMessages) {
            messages = initialMessages;
        }

        @Override
        public void onEvent(ReactionRemoved event, SlackSession session) {
            String emojiName = event.getEmojiName();
            for (SlackMessagePosted message : messages) {
                for (String reaction : message.getReactions().keySet()) {
                    if (emojiName.equals(reaction)) {
                        int count = message.getReactions().get(emojiName);
                        if (count == 1) {
                            message.getReactions().remove(emojiName);
                        } else {
                            message.getReactions().put(emojiName, --count);
                        }
                        return;
                    }
                }
            }
        }
    };

    public class ChannelHistoryMessagePostedListener implements SlackMessagePostedListener {

        List<SlackMessagePosted> messages = new ArrayList<>();

        public ChannelHistoryMessagePostedListener(List<SlackMessagePosted> initialMessages) {
            messages = initialMessages;
        }

        @Override
        public void onEvent(SlackMessagePosted event, SlackSession session) {
            messages.add(event);
        }
    }

    private String convertDateToSlackTimestamp(Date date) {
        return (date.getTime() / 1000) + ".123456";
    }

}
