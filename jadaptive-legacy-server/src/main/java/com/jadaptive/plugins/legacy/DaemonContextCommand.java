package com.jadaptive.plugins.legacy;

import com.jadaptive.plugins.legacy.commands.AbstractContextCommand;
import com.maverick.nio.DaemonContext;

public class DaemonContextCommand extends AbstractContextCommand<DaemonContext> {

	public DaemonContextCommand() {
		super("daemon-context",
				"Diagnostics",
				"daemon-context <method> <value>");
		setDescription("Configure the DaemonContext and report on its current settings");
	}

	@Override
	protected Class<?> getContextClass() {
		return DaemonContext.class;
	}

	@Override
	protected DaemonContext getContextObject() {
		return process.getContext().getDaemonContext();
	}

	@Override
	protected String getPropertiesFile() {
		return "daemon.properties";
	}

}
