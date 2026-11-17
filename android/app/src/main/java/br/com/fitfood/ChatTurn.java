package br.com.fitfood;

final class ChatTurn {
    final boolean user;
    final String text;
    final boolean includedPhoto;

    ChatTurn(boolean user, String text, boolean includedPhoto) {
        this.user = user;
        this.text = text;
        this.includedPhoto = includedPhoto;
    }
}
