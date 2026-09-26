package com.confirmspellbook;

import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

/**
 * The EventBus requires every @Subscribe method to be named "on" + the event's simple name and
 * throws on registration otherwise, which makes PluginManager stop the plugin the moment it starts.
 */
public class EventBusRegistrationTest
{
	@Test
	public void subscribersRegister()
	{
		new EventBus().register(new ConfirmSpellbookPlugin());
	}
}
