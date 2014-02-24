package com.voodoowarez.whenbox.server;

import io.undertow.servlet.api.DeploymentInfo;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.test.TestPortProvider;
import org.junit.Assert;

public class WhenboxServer {
	private static UndertowJaxrsServer server;

	static public void main(String[] args) throws Exception {
		DeploymentInfo di = server.undertowDeployment(MyApp.class);
		di.setContextPath("/di");
		di.setDeploymentName("DI");
		server.deploy(di);
		Client client = ClientBuilder.newClient();
		String val = client
				.target(TestPortProvider.generateURL("/di/base/test"))
				.request().get(String.class);
		Assert.assertEquals("hello world", val);
		client.close();
	}
}
