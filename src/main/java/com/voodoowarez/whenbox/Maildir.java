package com.voodoowarez.whenbox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.Principal;
import java.util.Properties;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.BasicConfigurator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;

@Path("/{domain}/{user}/mailbox")
@Provider
@SessionScoped
public class Maildir {

	static {
		BasicConfigurator.configure();
	}

	@PathParam("domain")
	private String domain;
	@PathParam("user")
	private String user;

	@Inject
	private Principal principal;

	@Inject
	private Store store;

	static private JsonFactory jsonFactory = new JsonFactory();
	static private int batchRead = 333;
	static private Flag[] flags = new Flag[]{Flags.Flag.ANSWERED, Flags.Flag.DELETED, Flags.Flag.DRAFT, Flags.Flag.FLAGGED, Flags.Flag.RECENT, Flags.Flag.SEEN, Flags.Flag.USER};
	
	@GET
	@Path("/list")
	@Produces(MediaType.APPLICATION_JSON)
	public Response list() {
	  return Response.ok(new MetaList()).build();
	}

	public String produceMailboxDir() {
		return "/home/"+user+".maildir";
	}

	@javax.enterprise.inject.Produces
	public Store produceStore() throws NoSuchProviderException {
		final Session session = Session.getInstance(new Properties());
		final String url = "maildir:"+produceMailboxDir();
		final Store store = session.getStore(new URLName(url));
		return store;
	}

	@javax.enterprise.inject.Produces
	public java.nio.file.Path produceMailboxPath() {
		final String dir = produceMailboxDir();
		return FileSystems.getDefault().getPath(dir);
	}

	public DirectoryStream<java.nio.file.Path> produceMailboxFiles() throws IOException {
		return Files.newDirectoryStream(produceMailboxPath());
	}

	private class FileList implements StreamingOutput {
		public void write(OutputStream os) throws IOException {
			final JsonGenerator generator = jsonFactory.createGenerator(os);
			final DirectoryStream<java.nio.file.Path> paths = produceMailboxFiles();
			generator.writeStartArray();
			for(java.nio.file.Path path : paths) {
				generator.writeString(path.getFileName().toString());
			}
			generator.writeEndArray();
		}
	}

	private class MetaList implements StreamingOutput {
		public void write(OutputStream os) throws IOException {
			final JsonGenerator generator = jsonFactory.createGenerator(os);
			try{
				final Folder inbox = store.getFolder("inbox");
				inbox.open(Folder.READ_WRITE);

				generator.writeStartArray();
				int i = 0, next= batchRead;
				while(true){
					final Message messages[] = inbox.getMessages(i, next);
					for(int j= 0; j< messages.length; ++j){
						final Message m = messages[j];
						generator.writeObjectFieldStart(m.getFileName());
						// labels
						generator.writeObjectField("size", m.getSize());
						generator.writeObjectField("date", m.getSentDate());
						generator.writeObjectField("subject", m.getSubject());
						generator.writeArrayFieldStart("markers");
						final Flags messageFlags = m.getFlags();
						for(Flag flag : flags){
							if(messageFlags.contains(flag))
								generator.writeString(flag.toString());
						}
						generator.writeEndArray();
						generateAddresses("from", m.getFrom(), generator);
						generateAddresses("to", m.getRecipients(RecipientType.TO), generator);
						generateAddresses("cc", m.getRecipients(RecipientType.CC), generator);
					}
					i = next;
					next += batchRead;
				}
				
			}catch(MessagingException e){
			}finally{
				generator.writeEndArray();
			}
			generator.close();			
		}
	}

	private static void generateAddresses(String header, Address[] addresses, JsonGenerator generator) throws JsonGenerationException, IOException{
		generator.writeArrayFieldStart(header);
		for(Address address : addresses) {
			if(address instanceof InternetAddress) {
				InternetAddress email = (InternetAddress) address;
				generator.writeObjectField("address", email.getAddress());
				generator.writeObjectField("name", email.getPersonal());
				generator.writeEndObject();
			}
		}
		generator.writeEndArray();
	}
}
