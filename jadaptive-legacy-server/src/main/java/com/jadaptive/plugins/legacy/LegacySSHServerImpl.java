package com.jadaptive.plugins.legacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jadaptive.api.app.ApplicationProperties;
import com.jadaptive.api.app.ConfigHelper;
import com.jadaptive.api.app.StartupAware;
import com.maverick.nio.Daemon;
import com.maverick.nio.DaemonContext;
import com.maverick.nio.LicenseManager;
import com.maverick.ssh.SshException;
import com.maverick.sshd.Connection;
import com.maverick.sshd.SshContext;
import com.maverick.sshd.auth.DefaultAuthenticationMechanismFactory;
import com.maverick.sshd.platform.FileSystem;
import com.maverick.sshd.platform.FileSystemFactory;
import com.maverick.sshd.platform.PermissionDeniedException;
import com.maverick.sshd.sftp.AbstractFileSystem;
import com.maverick.sshd.vfs.VFSFileFactory;
import com.maverick.sshd.vfs.VirtualFileFactory;
import com.maverick.sshd.vfs.VirtualMountTemplate;
import com.maverick.util.IOUtil;
import com.sshtools.publickey.InvalidPassphraseException;
import com.sshtools.publickey.SshKeyPairGenerator;
import com.sshtools.server.vshell.DefaultVirtualProcessFactory;
import com.sshtools.server.vshell.ShellCommandFactory;
import com.sshtools.server.vshell.VirtualChannelFactory;

@Service
public class LegacySSHServerImpl extends Daemon implements LegacySSHServer, StartupAware {

	static Logger log = LoggerFactory.getLogger(LegacySSHServerImpl.class);
	
	@Autowired
	private PasswordAuthenticator passwordAuthenticator;
	
	@Autowired
	private AuthorizedKeysAuthenticator authorizedKeysAuthenticator;
	
	@Override
	protected void configure(DaemonContext context) throws IOException, SshException {
		
		SshContext sshContext = new SshContext(this);
		configureDaemonContext(context);
		configureSshContext(sshContext);
		
		int port = ApplicationProperties.getValue("legacy.port", 4444);
		boolean extenalAccess = ApplicationProperties.getValue("legacy.externalAccess", true);

		sshContext.setAuthenicationMechanismFactory(new DefaultAuthenticationMechanismFactory());
		
		if(ApplicationProperties.getValue("legacy.permitPassword", true)) {
			sshContext.getAuthenticationMechanismFactory().addProvider(passwordAuthenticator);
		}
		
		sshContext.getAuthenticationMechanismFactory().addProvider(authorizedKeysAuthenticator);
		
		context.addListeningInterface(extenalAccess ? "::" : "::1", port, sshContext);

		@SuppressWarnings("unchecked")
		ShellCommandFactory shellFactory = new ShellCommandFactory();
		shellFactory.installCommand(SshContextCommand.class);
		shellFactory.installCommand(DaemonContextCommand.class);
		
		sshContext.setChannelFactory(new VirtualChannelFactory(shellFactory, 
				new DefaultVirtualProcessFactory()));
		
		// Tell the SSHD which file system were using
		sshContext.setFileSystemProvider(new FileSystemFactory() {

			public FileSystem createInstance(Connection con,
					String protocolInUse) throws PermissionDeniedException,
					IOException {
				AbstractFileSystem fs = new AbstractFileSystem(
						new VirtualFileFactory(
								new VirtualMountTemplate("/", "tmp/${username}", 
										new VFSFileFactory())), con, protocolInUse);
				return fs;
			}
		});
		try {
//			sshContext.loadOrGenerateHostKey(
//					new File(ApplicationProperties.getConfFolder(), "legacy_host_key_ed25519"), 
//					SshKeyPairGenerator.ED25519, 0);
			sshContext.loadOrGenerateHostKey(
					new File(ApplicationProperties.getConfFolder(), "legacy_host_key_ecdsa_256"), 
					SshKeyPairGenerator.ECDSA, 256);
			sshContext.loadOrGenerateHostKey(
					new File(ApplicationProperties.getConfFolder(), "legacy_host_key_ecdsa_384"), 
					SshKeyPairGenerator.ECDSA, 384);
			sshContext.loadOrGenerateHostKey(
					new File(ApplicationProperties.getConfFolder(), "legacy_host_key_ecdsa_521"), 
					SshKeyPairGenerator.ECDSA, 521);
			sshContext.loadOrGenerateHostKey(
					new File(ApplicationProperties.getConfFolder(), "legacy_host_key_rsa_2048"), 
					SshKeyPairGenerator.SSH2_RSA, 2048);
		} catch (InvalidPassphraseException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
		
	}
	
	private boolean isSetter(Method m) {
		
		if(!m.getName().startsWith("set")) {
			return false;
		}
		
		if(m.getParameterCount()!=1) {
			return false;
		}
		
		try {
			Class<?> type = m.getParameterTypes()[0];
			if(type.equals(int.class)) {
				return true;
			} else if(type.equals(long.class)) {
				return true;
			} else if(type.equals(boolean.class)) {
				return true;
			} else if(type.equals(String.class)) {
				return true;
			}
		} catch (Throwable e) {
		} 
		return false;
	}
	
	private void setValue(Method m, String val, Object ctx) {
		
		try {
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
		} catch (Throwable e) {
			log.error("Could not set value {}", m.getName().substring(3), e);
		} 
	}

	private void configureSshContext(SshContext sshContext) {
		
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(new File(ConfigHelper.getConfFolder(), "sshd.properties")));
			for(Method m : SshContext.class.getMethods()) {
				if(isSetter(m) && properties.containsKey(m.getName().substring(3))) {
					setValue(m, properties.getProperty(m.getName().substring(3)), sshContext);
					log.info("Set SshContext value {} to {}", 
							m.getName().substring(3), 
							properties.getProperty(m.getName().substring(3)));
				}
			}
		} catch (IOException e) {
			log.error("Failed to load sshd.properties", e);
		}
		
	}

	private void configureDaemonContext(DaemonContext context) {
		
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(new File(ConfigHelper.getConfFolder(), "daemon.properties")));
			for(Method m : DaemonContext.class.getMethods()) {
				if(isSetter(m) && properties.containsKey(m.getName().substring(3))) {
					setValue(m, properties.getProperty(m.getName().substring(3)), context);
					log.info("Set DaemonContext value {} to {}", 
							m.getName().substring(3), 
							properties.getProperty(m.getName().substring(3)));
				}
			}
		} catch (IOException e) {
			log.error("Failed to load daemon.properties", e);
		}
	}

	@Override
	public void onApplicationStartup() {
		
		File licenseFile = new File(ApplicationProperties.getConfFolder(), "sshd-license.txt");
		if(!licenseFile.exists()) {
			log.info("Missing sshd-license.txt in conf folder");
			return;
		}
		
		try {
			LicenseManager.addLicense(IOUtil.toString(new FileInputStream(licenseFile), "UTF-8"));
			
			startup();
		} catch (IOException e) {
			log.error("SSHD service failed to start", e);
		}
	}

	@Override
	public Integer getStartupPosition() {
		return 0;
	}

}
