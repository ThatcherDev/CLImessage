package dev.thatcherclough.climessage;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Erase;

public class Messenger {

	private String address = null;
	private String password = null;
	private String recipient = null;
	private ArrayList<String> messages = new ArrayList<String>();
	private PrintWriter out = null;

	/**
	 * Constructs a new Messenger.
	 * <p>
	 * Gets address and password of G-Mail account from "userdata/creds.properties".
	 * Parses {@link filename} to get phone number associated with conversation.
	 * Uses {@link EmailUtils#getEmail(String)} to get the email address that
	 * forwards to the phone number. Gets previous conversation messages from file
	 * with name {@link filename}. Sets {@link out} to the file with name
	 * {@link filename}.
	 * 
	 * @param filename file containing previous messages
	 * @return
	 * @throws IOException
	 */
	public Messenger(String filename) throws IOException {
		System.out.print("Connecting...");
		Properties creds = new Properties();
		creds.load(new FileInputStream("userdata" + File.separator + "creds.properties"));
		address = creds.getProperty("address");
		password = creds.getProperty("password");
		recipient = EmailUtils.getEmail(filename.substring(filename.lastIndexOf("-") + 1, filename.lastIndexOf(".")));
		Scanner in = new Scanner(new File(filename));
		while (in.hasNextLine())
			messages.add(0, in.nextLine());
		in.close();
		out = new PrintWriter(new FileOutputStream(new File(filename), true), true);
	}

	/**
	 * Starts messenger.
	 * <p>
	 * Uses {@link RawConsole#enable()} to enable "raw" mode on the current unix
	 * console. Displays previous messages. Starts a new thread {@link getInput} to
	 * constantly get keyboard input for messages to send. Starts a new thread
	 * {@link receiveMessages} to constantly get messages that have been sent to
	 * {@link #address} from {@link #recipient}.
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public void start() throws InterruptedException, IOException {
		RawConsole.enable();
		System.out.print(Ansi.ansi().eraseScreen().cursor(0, 0));
		RawConsole.println("Press CTRL + C to exit.");
		RawConsole.println("Type a message and press ENTER to send:\n");
		dispMessages();
		Thread getInput = new Thread() {
			public void run() {
				Reader reader = System.console().reader();
				while (true) {
					try {
						System.out.print(">");
						String message = RawConsole.getInput(reader);
						String timestamp = "["
								+ DateTimeFormatter.ofPattern("MM/dd/YYYY HH:mm:ss").format(LocalDateTime.now()) + "]";
						String finalMessage = message + "  -  [sent] " + timestamp;
						messages.add(0, finalMessage);
						dispMessages();
						out.println(finalMessage);
						sendMessage(message);
						System.out.print(Ansi.ansi().eraseLine(Erase.BACKWARD).cursorToColumn(0));
					} catch (Exception e) {
						error(e);
					}
				}
			}
		};

		Thread receiveMessages = new Thread() {
			public void run() {
				String number = recipient.substring(0, recipient.indexOf("@"));
				while (true) {
					try {
						String message = EmailUtils.recEmail(address, password, number);
						if (message != null) {
							String timestamp = "["
									+ DateTimeFormatter.ofPattern("MM/dd/YYYY HH:mm:ss").format(LocalDateTime.now())
									+ "]";
							if (message.contains("\r"))
								message = message.substring(0, message.indexOf("\r"));
							if (message.contains("\n"))
								message = message.substring(0, message.indexOf("\n"));
							String finalMessage = message + "  -  [received] " + timestamp;
							messages.add(0, finalMessage);
							dispMessages();
							out.println(finalMessage);
							playNotificationSound();
						}
						Thread.sleep(3000);
					} catch (Exception e) {
						error(e);
					}
				}
			}
		};
		getInput.start();
		receiveMessages.start();
	}

	/**
	 * Saves cursor position, moves cursor down 2 lines, displays the first 5
	 * elements of {@ink #messages}, and restores cursor position.
	 */
	private void dispMessages() {
		System.out.print("\0337"); // saves cursor position
		System.out.print(Ansi.ansi().cursorDown(2));
		for (int k = 0; (k < messages.size() && k < 10); k++)
			RawConsole.println(Ansi.ansi().eraseLine() + messages.get(k));
		System.out.print("\0338"); // restores cursor position
	}

	/**
	 * Creates and starts new thread {@link send} to send {@link message} to
	 * {@link #recipient}.
	 * 
	 * @param message message to send
	 */
	private void sendMessage(String message) {
		Thread send = new Thread() {
			public void run() {
				try {
					Thread.sleep(3000);
					EmailUtils.sendEmail(address, password, recipient, message);
				} catch (Exception e) {
					error(e);
				}
			}
		};
		send.start();
	}

	/**
	 * When executed from a jar file, plays "BOOT-INF/classes/notification.wav"
	 * which is a copy of "src/main/resources/notification.wav".
	 */
	private void playNotificationSound() {
		try {
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
					new BufferedInputStream(getClass().getResourceAsStream("/BOOT-INF/classes/notification.wav")));
			Clip clip = AudioSystem.getClip();
			clip.open(audioInputStream);
			clip.start();
		} catch (Exception e) {
		}
	}

	/**
	 * Disables "raw" mode on the current unix console, clears console, and prints
	 * stack trace of Exception {@link e}
	 * 
	 * @param e Exception to print stack trace of
	 */
	private static void error(Exception e) {
		try {
			RawConsole.disable();
			System.out.print(Ansi.ansi().eraseScreen().cursor(0, 0));
			e.printStackTrace();
		} catch (Exception e1) {
		} finally {
			System.exit(0);
		}
	}
}