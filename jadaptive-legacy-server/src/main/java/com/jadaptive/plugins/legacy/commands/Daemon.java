package com.jadaptive.plugins.legacy.commands;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.maverick.sshd.SshContext;
import com.maverick.sshd.platform.PermissionDeniedException;
import com.sshtools.server.vshell.ShellCommand;
import com.sshtools.server.vshell.VirtualProcess;

public class Daemon extends ShellCommand {

	public Daemon() {
		super("daemon", "Diagnostics");
	}

	@Override
	public void run(CommandLine args, VirtualProcess process) throws IOException, PermissionDeniedException {
		
		if(args.getArgs().length == 1) {
			
			for(Method m : SshContext.class.getMethods()) {
				if(m.getName().startsWith("get") 
						&& (m.getReturnType().isPrimitive() || m.getReturnType().equals(String.class))) {
					printValue(m, process.getContext());
				}
			}
		} else if(args.getArgs().length > 1) {
			String method = args.getArgs()[1];
			if(method.startsWith("get")) {
				for(Method m : SshContext.class.getMethods()) {
					if(m.getName().equals(method)) {
						printValue(m, process.getContext());
					}
				}
			} else if(method.startsWith("set")) {
				
				for(Method m : SshContext.class.getMethods()) {
					if(m.getName().equals(method)) {
						try {
							Method get = findGetter("get" + method.substring(3));
							Object previous = get.invoke(process.getContext());
							setPrimitiveValue(m, args.getArgs()[2], process.getContext());
							Object now = get.invoke(process.getContext());
							process.getConsole().printStringNewline(String.format(
									"%s changed from %s to %s", get.getName(), previous.toString(), now.toString()));
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							throw new IOException(e.getMessage(), e);
						}
					}
				}
			}
		}

	}
	
	private void printValue(Method m, SshContext context) throws IOException {
		try {
			Object val = m.invoke(context);
			process.getConsole().printStringNewline(String.format("%1$-65s: %2$s", 
					m.getName().substring(3), 
					val == null ? "" : val.toString()));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
		}
	}

	private Method findGetter(String name) {
		for(Method m : SshContext.class.getMethods()) {
			if(m.getName().equals(name)) {
				return m;
			}
		}
		throw new IllegalArgumentException();
	}
	
	private void setPrimitiveValue(Method m, String val, SshContext ctx) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		Class<?> type = m.getParameterTypes()[0];
		if(type.equals(int.class)) {
			m.invoke(ctx, Integer.parseInt(val));
		} else if(type.equals(long.class)) {
			m.invoke(ctx, Long.parseLong(val));
		} else if(type.equals(boolean.class)) {
			m.invoke(ctx, Boolean.parseBoolean(val));
		} else if(type.equals(String.class)) {
			m.invoke(ctx, val);
		} 
	}

	@Override
	public int complete(String buffer, int cursor, List<String> candidates) {
		
		for(Method m : SshContext.class.getMethods()) {
			
			if(m.getName().startsWith("set")) {
				if(m.getParameterTypes().length==1 && (m.getParameterTypes()[0].isPrimitive() || m.getParameterTypes()[0].equals(String.class))) {
					if(m.getName().startsWith(buffer)) {
						candidates.add(m.getName());
					}
				} 
			} else if(m.getName().startsWith("get") && (m.getReturnType().isPrimitive() || m.getReturnType().equals(String.class))) {
				if(m.getName().startsWith(buffer)) {
					candidates.add(m.getName());
				}
			}
		}
		return 0;
	}

}
