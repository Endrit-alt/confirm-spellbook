package com.confirmspellbook;

public enum MissingCondition
{
    BOOK_OF_THE_DEAD("Missing Book of the Dead", "Book!"),
    SPELLBOOK_CONFIRMATION("Confirm spellbook", "Spellbook!"),
    THRALL_RUNES("Missing thrall runes", "Runes!"),
    NONE("", "");

    private final String longText;
    private final String shortText;

    MissingCondition(String longText, String shortText)
    {
        this.longText = longText;
        this.shortText = shortText;
    }

    public String getLongText()
    {
        return longText;
    }

    public String getShortText()
    {
        return shortText;
    }
}
