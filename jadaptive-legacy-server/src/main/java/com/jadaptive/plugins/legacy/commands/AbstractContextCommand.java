package com.jadaptive.plugins.legacy.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import com.jadaptive.api.app.ConfigHelper;
import com.maverick.sshd.platform.PermissionDeniedException;
import com.sshtools.server.vshell.ShellCommand;
import com.sshtools.server.vshell.VirtualProcess;

public abstract class AbstractContextCommand<T> extends ShellCommand {

	
	public AbstractContextCommand(String name, String subsystem, String signature, Option... options) {
		super(name, subsystem, signature, options);
	}

	public AbstractContextCommand(String name, String subsystem, String signature) {
		super(name, subsystem, signature);
	}

	public AbstractContextCommand(String name, String subsystem) {
		super(name, subsystem);
	}

	@Override
	public void run(CommandLine args, VirtualProcess process) throws IOException, PermissionDeniedException {
		
		if(args.getArgs().length == 1) {
			
			for(Method m : getContextClass().getMethods()) {
				if(m.getName().startsWith("get") 
						&& (m.getReturnType().isPrimitive() || m.getReturnType().equals(String.class))) {
					printValue(m, getContextObject());
				}
			}
		} else if(args.getArgs().length > 1) {
			
			if("save".equals(args.getArgs()[1])) {
				
				Properties properties = new Properties();
				
				for(Method m : getContextClass().getMethods()) {
					if(m.getName().startsWith("get") 
							&& (m.getReturnType().isPrimitive() || m.getReturnType().equals(String.class))) {
						try {
							Object val = m.invoke(getContextObject());
							properties.put(m.getName().substring(3), val==null ? "" : val.toString());
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						}
					}
				}
				
				properties.store(new FileOutputStream(new File(ConfigHelper.getConfFolder(), getPropertiesFile())), "");
				process.getConsole().printStringNewline("Saved to " + getPropertiesFile());
			} else {
				String method = args.getArgs()[1];
				if(method.startsWith("get")) {
					for(Method m : getContextClass().getMethods()) {
						if(m.getName().equals(method)) {
							printValue(m, getContextObject());
						}
					}
				} else if(method.startsWith("set")) {
					
					for(Method m : getContextClass().getMethods()) {
						if(m.getName().equals(method)) {
							try {
								Method get = findGetter("get" + method.substring(3));
								Object previous = get.invoke(getContextObject());
								setPrimitiveValue(m, args.getArgs()[2], getContextObject());
								Object now = get.invoke(getContextObject());
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

	}
	
	protected abstract Class<?> getContextClass();

	protected abstract T getContextObject();
	
	protected abstract String getPropertiesFile();
	
	private void printValue(Method m, T context) throws IOException {
		try {
			Object val = m.invoke(context);
			process.getConsole().printStringNewline(String.format("%1$-65s: %2$s", 
					m.getName().substring(3), 
					val == null ? "" : val.toString()));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
		}
	}

	private Method findGetter(String name) {
		for(Method m : getContextClass().getMethods()) {
			if(m.getName().equals(name)) {
				return m;
			}
		}
		throw new IllegalArgumentException();
	}
	
	private void setPrimitiveValue(Method m, String val, T ctx) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
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
		
		for(Method m : getContextClass().getMethods()) {
			
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
