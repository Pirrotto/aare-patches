package app.template.extension.aare;

/** The voice reply's spoken phrases, worded as Android Auto's Google voice says them. Plain Java, for tests. */
final class ReplyPhrases {
    private ReplyPhrases() {
    }

    /**
     * AARE reads a new message as "Name. text": with the conversation's [title] as the name (a
     * one-to-one chat) it becomes "Name says: text", as Google reads it. Anything else (a group, a
     * whole conversation with several senders) stays as it is.
     */
    static String withSays(String spoken, String title, boolean italian) {
        if (spoken == null || title == null || title.isEmpty()) return spoken;
        String prefix = title + ". ";
        if (!spoken.startsWith(prefix)) return spoken;
        String rest = spoken.substring(prefix.length());
        if (rest.isEmpty()) return spoken;
        // "Group. Anna: text. Marco: text": several senders, read as AARE reads them.
        int colon = rest.indexOf(": ");
        if (colon > 0 && colon <= 40 && rest.indexOf(". ") > colon) return spoken;
        if (colon > 0 && colon <= 40 && rest.indexOf(". ") < 0) return spoken;
        return title + (italian ? " dice: " : " says: ") + rest;
    }

    static String askReply(boolean it) {
        return it ? "Vuoi rispondere?" : "Do you want to reply?";
    }

    static String askReplyAgain(boolean it) {
        return it ? "Scusa, vuoi rispondere?" : "Sorry, do you want to reply?";
    }

    static String whatMessage(boolean it) {
        return it ? "Qual è il messaggio?" : "What's the message?";
    }

    static String readBack(String to, String message, boolean it) {
        return it ? "Ecco il tuo messaggio per " + to + ": " + message + ". Vuoi mandarlo?"
                : "Here's your message to " + to + ": " + message + ". Do you want to send it?";
    }

    static String sendAgain(boolean it) {
        return it ? "Scusa, vuoi mandarlo?" : "Sorry, do you want to send it?";
    }

    static String sent(String to, boolean it) {
        return it ? "Messaggio inviato a " + to + "." : "Message sent to " + to + ".";
    }

    static String notSent(boolean it) {
        return it ? "Non sono riuscito a mandare il messaggio." : "I couldn't send the message.";
    }

    static String cancelled(boolean it) {
        return it ? "Ok, non lo mando." : "OK, I won't send it.";
    }
}
