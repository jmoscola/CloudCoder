package org.cloudcoder.app.wizard.exec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

public class Bootstrap {
	private static final Logger logger = LoggerFactory.getLogger(Bootstrap.class);
	
	// Real download site
	//public static final String DOWNLOAD_SITE = "https://s3.amazonaws.com/cloudcoder-binaries";

	// Temporary site for development
	public static final String DOWNLOAD_SITE = "http://faculty.ycp.edu/~dhovemey/cloudcoder";
	
	public static final String BOOTSTRAP_SCRIPT = DOWNLOAD_SITE + "/bootstrap.pl";
	
	private static class Drain implements Runnable {
		private InputStream is;
		private OutputStream os;
		
		public Drain(InputStream is, OutputStream os) {
			this.is = is;
			this.os = os;
		}
		
		@Override
		public void run() {
			try {
				IOUtils.copy(is, os);
			} catch (IOException e) {
				logger.error("Error draining stream ", e);
			} finally {
				IOUtils.closeQuietly(is);
				// Note: do NOT close the output stream
			}
		}
	}
	
	private ICloudInfo info;

	public Bootstrap(ICloudInfo info) {
		this.info = info;
	}
	
	public void bootstrapWebappServer() throws ExecException {
		executeCommand("wget " + BOOTSTRAP_SCRIPT);
	}

	/**
	 * Execute remote command on the webapp server,
	 * diverting the remote process's stdout and stderr to
	 * System.out and System.err.
	 * 
	 * @param cmdStr  the command to execute
	 * @throws ExecException
	 */
	private void executeCommand(String cmdStr) throws ExecException {
		try {
			SSHClient ssh = new SSHClient();
			ssh.addHostKeyVerifier(new PromiscuousVerifier()); // FIXME: would be nice to have actual host key fingerprint
			try {
				ssh.connect(info.getWebappPublicIp());
				KeyProvider keys = ssh.loadKeys(info.getPrivateKeyFile().getAbsolutePath());
				ssh.authPublickey(info.getWebappServerUserName(), keys);
				Session session = ssh.startSession();
				session.setEnvVar("LANG", "en_US.UTF-8");
				try {
					System.out.println("Executing command: " + cmdStr);
					Command cmd = session.exec(cmdStr);

					// Divert command output and error
					Thread t1 = new Thread(new Drain(cmd.getInputStream(), System.out));
					Thread t2 = new Thread(new Drain(cmd.getErrorStream(), System.err));
					t1.start();
					t2.start();
					t1.join();
					t2.join();
					
					cmd.join(10, TimeUnit.SECONDS);
					System.out.println("Command exit code is " + cmd.getExitStatus());
				} finally {
					session.close();
				}
			} finally {
				ssh.close();
			}
			
		} catch (Exception e) {
			throw new ExecException("Error bootstrapping CloudCoder on webapp instance", e);
		}
	}
	
	// This is just for testing
	private static class TestCloudInfo extends AbstractCloudInfo implements ICloudInfo {
		private String username;
		private String hostAddress;
		private String keyPairFilename;

		public TestCloudInfo(String username, String hostAddress, String keyPairFilename) {
			this.username = username;
			this.hostAddress = hostAddress;
			this.keyPairFilename = keyPairFilename;
		}

		@Override
		public String getWebappPublicIp() {
			return hostAddress;
		}

		@Override
		public boolean isPrivateKeyGenerated() {
			throw new UnsupportedOperationException();
		}

		@Override
		public File getPrivateKeyFile() {
			return new File(keyPairFilename);
		}

		@Override
		public String getWebappPrivateIp() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getWebappServerUserName() {
			return username;
		}
		
	}

	// This is just for testing
	public static void main(String[] args) throws ExecException {
		@SuppressWarnings("resource")
		Scanner keyboard = new Scanner(System.in);
		System.out.print("Host username: ");
		String username = keyboard.nextLine();
		System.out.print("Host address: ");
		String hostAddress = keyboard.nextLine();
		System.out.print("Keypair file: ");
		String keyPairFilename = keyboard.nextLine();

		TestCloudInfo info = new TestCloudInfo(username, hostAddress, keyPairFilename);
		
		Bootstrap bootstrap = new Bootstrap(info);
		
		bootstrap.bootstrapWebappServer();
	}
}
