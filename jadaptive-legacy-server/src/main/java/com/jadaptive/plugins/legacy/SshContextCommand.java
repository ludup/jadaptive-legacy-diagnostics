package com.jadaptive.plugins.legacy;

import com.jadaptive.plugins.legacy.commands.AbstractContextCommand;
import com.maverick.sshd.SshContext;

public class SshContextCommand extends AbstractContextCommand<SshContext> {

	public SshContextCommand() {
		super("sshd-context",
				"Diagnostics",
				"sshd-context <method> <value>");
		setDescription("Configure the SshContext and report on its current settings");
	}

	@Override
	protected Class<?> getContextClass() {
		return SshContext.class;
	}

	@Override
	protected SshContext getContextObject() {
		return process.getContext();
	}

	@Override
	protected String getPropertiesFile() {
		return "sshd.properties";
	}
}
