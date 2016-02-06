package org.irods.jargon.rest.commands.ticket;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.rest.auth.DefaultHttpClientAndContext;
import org.irods.jargon.rest.auth.RestAuthUtils;
import org.irods.jargon.rest.utils.RestTestingProperties;
import org.irods.jargon.testutils.IRODSTestSetupUtilities;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.testutils.filemanip.FileGenerator;
import org.irods.jargon.testutils.filemanip.ScratchFileUtils;
import org.irods.jargon.ticket.Ticket;
import org.irods.jargon.ticket.TicketAdminService;
import org.irods.jargon.ticket.TicketServiceFactoryImpl;
import org.irods.jargon.ticket.packinstr.TicketCreateModeEnum;
import org.irods.jargon.ticket.utils.TicketRandomString;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.plugins.server.tjws.TJWSEmbeddedJaxrsServer;
import org.jboss.resteasy.plugins.spring.SpringBeanProcessor;
import org.jboss.resteasy.plugins.spring.SpringResourceFactory;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

/**
 * @author Justin James- RENCI (www.irods.org)
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:jargon-beans.xml",
		"classpath:rest-servlet.xml" })
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
		DirtiesContextTestExecutionListener.class })
public class TicketServiceTest  implements ApplicationContextAware {

	private static TJWSEmbeddedJaxrsServer server;

	private static ApplicationContext applicationContext;

	private static Properties testingProperties = new Properties();
	private static TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
	private static IRODSFileSystem irodsFileSystem;
	private static ScratchFileUtils scratchFileUtils = null;
	private static IRODSTestSetupUtilities irodsTestSetupUtilities = null;
	private static String absPath = null;
	
	private static String targetIrodsFile = null;
	
	public static final String IRODS_TEST_SUBDIR_PATH = "TicketTestDirectory";
	public static final SimpleDateFormat EXPIRATION_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static TicketServiceFactoryImpl ticketServiceFactory;
	private static TicketAdminService ticketService;
	
	private static LinkedHashMap<String, String> restrictionTypeValueMap = new LinkedHashMap<>();

	@Override
	public void setApplicationContext(final ApplicationContext context)
			throws BeansException {
		applicationContext = context;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingProperties = testingPropertiesLoader.getTestProperties();
		scratchFileUtils = new ScratchFileUtils(testingProperties);
		scratchFileUtils.clearAndReinitializeScratchDirectory(IRODS_TEST_SUBDIR_PATH);
		irodsTestSetupUtilities = new IRODSTestSetupUtilities();
		irodsTestSetupUtilities.initializeIrodsScratchDirectory();
		irodsTestSetupUtilities.initializeDirectoryForTest(IRODS_TEST_SUBDIR_PATH);
		irodsFileSystem = IRODSFileSystem.instance();
		
		// create a test data object
		String testFileName = "testfile.dat";
		
		targetIrodsFile = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testFileName);

		
		absPath = scratchFileUtils.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH);
		String localFileName = FileGenerator.generateFileOfFixedLengthGivenName(absPath, testFileName, 20);
		
		IRODSAccount irodsAccount = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);

		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem.getIRODSAccessObjectFactory();

		DataTransferOperations dto = accessObjectFactory.getDataTransferOperations(irodsAccount);
		dto.putOperation(localFileName, targetIrodsFile, testingProperties
				.getProperty(TestingPropertiesHelper.IRODS_RESOURCE_KEY), null,
				null);
		
		
		ticketServiceFactory = new TicketServiceFactoryImpl(accessObjectFactory);
		ticketService = ticketServiceFactory.instanceTicketAdminService(irodsAccount);

		restrictionTypeValueMap.put("add_host", "localhost");
		restrictionTypeValueMap.put("remove_host", "localhost");
		restrictionTypeValueMap.put("add_group",
				testingProperties.getProperty("jargon.test.user.group"));
		restrictionTypeValueMap.put("remove_group",
				testingProperties.getProperty("jargon.test.user.group"));
		restrictionTypeValueMap.put("add_user",
				testingProperties.getProperty("test.irods.user"));
		restrictionTypeValueMap.put("remove_user",
				testingProperties.getProperty("test.irods.user"));
		restrictionTypeValueMap.put("byte_write_limit", "100");
		restrictionTypeValueMap.put("file_write_limit", "200");
		restrictionTypeValueMap.put("uses_limit", "25");
		restrictionTypeValueMap.put(
				"expiration",
				EXPIRATION_DATE_FORMAT.format(new Date(new Date().getTime()
						+ 365 * 24 * 60 * 60 * 1000L)));

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (server != null) {
			server.stop();
		}
		irodsFileSystem.closeAndEatExceptions();
	}

	@Before
	public void setUp() throws Exception {

		if (server != null) {
			return;
		}

		int port = testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY);
		server = new TJWSEmbeddedJaxrsServer();
		server.setPort(port);
		ResteasyDeployment deployment = server.getDeployment();

		server.start();
		Dispatcher dispatcher = deployment.getDispatcher();
		SpringBeanProcessor processor = new SpringBeanProcessor(dispatcher,
				deployment.getRegistry(), deployment.getProviderFactory());
		((ConfigurableApplicationContext) applicationContext)
				.addBeanFactoryPostProcessor(processor);

		SpringResourceFactory noDefaults = new SpringResourceFactory(
				"ticketService", applicationContext,
				TicketService.class);
		dispatcher.getRegistry().addResourceFactory(noDefaults);
		
	}

	@Test
	public void testCreateReadTicketDefaultStringXml() throws Exception {
	
		String requestBody = "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
			+ "<mode>read</mode>"
			+ "<object_path>" + targetIrodsFile + "</object_path>"
			+ "</ns2:ticket>";

		
		String expectedResponse_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
				+ "<ticket_string>"; 
		String expectedResponse_2 = "</ticket_string>"
				+ "</ns2:ticket>"; 
		
		String ticketString = null;

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/xml");
			httppost.addHeader("Accept", "application/xml");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse_1);
			int index2 = entityData.indexOf(expectedResponse_2);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_1,
					index1 > -1);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_2,
					index2 > -1);
			
			int ticketLength = index2 - index1 - expectedResponse_1.length();
			
			Assert.assertTrue(
					"Ticket length was " + ticketLength + " Expected: 15",
					ticketLength == 15);
			
			ticketString = entityData.substring(index1+expectedResponse_1.length(), index1+expectedResponse_1.length()+15);
			System.out.println("ticketId="+ticketString);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);
			
			// check that the ticket is read only
			Assert.assertTrue("Ticket type was not correct.  Expected read but detected " + t.getType(), "read".equalsIgnoreCase(t.getType().getTextValue()));

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			if (ticketString != null) {
				ticketService.deleteTicket(ticketString);
			}
		}
	}
	
	
	@Test
	public void testCreateWriteTicketDefaultStringXml() throws Exception {
	
		String requestBody = "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
			+ "<mode>write</mode>"
			+ "<object_path>" + targetIrodsFile + "</object_path>"
			+ "</ns2:ticket>";

		
		String expectedResponse_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
				+ "<ticket_string>"; 
		String expectedResponse_2 = "</ticket_string>"
				+ "</ns2:ticket>"; 
		
		String ticketString = null;

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/xml");
			httppost.addHeader("Accept", "application/xml");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse_1);
			int index2 = entityData.indexOf(expectedResponse_2);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_1,
					index1 > -1);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_2,
					index2 > -1);
			
			int ticketLength = index2 - index1 - expectedResponse_1.length();
			
			Assert.assertTrue(
					"Ticket length was " + ticketLength + " Expected: 15",
					ticketLength == 15);
			
			ticketString = entityData.substring(index1+expectedResponse_1.length(), index1+expectedResponse_1.length()+15);
			System.out.println("ticketId="+ticketString);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);
			
			// check that the ticket is read only
			Assert.assertTrue("Ticket type was not correct.  Expected read but detected " + t.getType(), "write".equalsIgnoreCase(t.getType().getTextValue()));

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			if (ticketString != null) {
				ticketService.deleteTicket(ticketString);
			}
		}
	}
	
	@Test
	public void testCreateReadTicketDefaultStringJson() throws Exception {
	
		String requestBody = "{\"mode\":\"read\",\"object_path\":\""
				+ targetIrodsFile + "\"}";
		
		String expectedResponse_1 = "{\"ticket_string\":\""; 
		String expectedResponse_2 = "\"}"; 
		
		String ticketString = null;

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/json");
			httppost.addHeader("Accept", "application/json");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse_1);
			int index2 = entityData.indexOf(expectedResponse_2);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_1,
					index1 > -1);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_2,
					index2 > -1);
			
			int ticketLength = index2 - index1 - expectedResponse_1.length();
			
			Assert.assertTrue(
					"Ticket length was " + ticketLength + " Expected: 15",
					ticketLength == 15);
			
			ticketString = entityData.substring(index1+expectedResponse_1.length(), index1+expectedResponse_1.length()+15);
			System.out.println("ticketId="+ticketString);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);
			
			// check that the ticket is read only
			Assert.assertTrue("Ticket type was not correct.  Expected read but detected " + t.getType(), "read".equalsIgnoreCase(t.getType().getTextValue()));

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			if (ticketString != null) {
				ticketService.deleteTicket(ticketString);
			}
		}
	}
	
	
	@Test
	public void testCreateWriteTicketDefaultStringJson() throws Exception {
	
		String requestBodyXml = "{\"mode\":\"write\",\"object_path\":\""
				+ targetIrodsFile + "\"}";;
		
		String expectedResponse_1 = "{\"ticket_string\":\""; 
		String expectedResponse_2 = "\"}"; 
		
		String ticketString = null;
		
		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/json");
			httppost.addHeader("Accept", "application/json");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBodyXml.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse_1);
			int index2 = entityData.indexOf(expectedResponse_2);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBodyXml + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_1,
					index1 > -1);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBodyXml + " Received: " + entityData 
                                         + "Expected: " + expectedResponse_2,
					index2 > -1);
			
			int ticketLength = index2 - index1 - expectedResponse_1.length();
			
			Assert.assertTrue(
					"Ticket length was " + ticketLength + " Expected: 15",
					ticketLength == 15);
			
			ticketString = entityData.substring(index1+expectedResponse_1.length(), index1+expectedResponse_1.length()+15);
			System.out.println("ticketId="+ticketString);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);
			
			// check that the ticket is read only
			Assert.assertTrue("Ticket type was not correct.  Expected read but detected " + t.getType(), "write".equalsIgnoreCase(t.getType().getTextValue()));

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			if (ticketString != null) {
				ticketService.deleteTicket(ticketString);
			}

		}
	}
	
	@Test
	public void createTicketWithSpecifiedStringXml() throws Exception {
	
		String ticketString = new TicketRandomString(15).nextString();
		
		String requestBody = "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
			+ "<mode>read</mode>"
			+ "<object_path>" + targetIrodsFile + "</object_path>"
			+ "<ticket_string>" + ticketString + "</ticket_string>"
			+ "</ns2:ticket>";

		
		String expectedResponse = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
				+ "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
				+ "<ticket_string>" + ticketString + "</ticket_string>"
				+ "</ns2:ticket>"; 

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/xml");
			httppost.addHeader("Accept", "application/xml");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse,
					index1 > -1);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			ticketService.deleteTicket(ticketString);
		}
	}
	
	@Test
	public void createTicketWithSpecifiedStringJson() throws Exception {
	
		String ticketString = new TicketRandomString(15).nextString();
		
		String requestBody = "{\"mode\":\"read\",\"object_path\":\""
				+ targetIrodsFile + "\","
				+ "\"ticket_string\":\"" + ticketString + "\"}";

		
		String expectedResponse = "{\"ticket_string\":\""
				+ ticketString + "\"}"; 

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);

		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket");

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpPost httppost = new HttpPost(sb.toString());
			httppost.addHeader("Content-Type", "application/json");
			httppost.addHeader("Accept", "application/json");
			
			HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
			httppost.setEntity(requestEntity);

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httppost, clientAndContext.getHttpContext());
			HttpEntity entity = response.getEntity();
			Assert.assertEquals(200, response.getStatusLine().getStatusCode());
			Assert.assertNotNull(entity);
			
			String entityData = EntityUtils.toString(entity);
			EntityUtils.consume(entity);
			System.out.println("XML>>>");
			System.out.println(entityData);
			
			int index1 = entityData.indexOf(expectedResponse);
			
			Assert.assertTrue(
					"Did not get expected xml stuff.  Sent: " + requestBody + " Received: " + entityData 
                                         + "Expected: " + expectedResponse,
					index1 > -1);
			
			// check that the ticket actually exists
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			ticketService.deleteTicket(ticketString);
		}
	}
	
	@Test (expected=DataNotFoundException.class)
	public void deleteTicket() throws Exception {
	
		// create a ticket to be deleted bye REST call
		String ticketString = new TicketRandomString(15).nextString();
		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();
		IRODSFile file = accessObjectFactory.getIRODSFileFactory(irodsAccount).instanceIRODSFile(targetIrodsFile);
		ticketService.createTicket(TicketCreateModeEnum.READ, file, ticketString);

		// Send REST request to delete the ticket
		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:");
		sb.append(testingPropertiesHelper.getPropertyValueAsInt(
				testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
		sb.append("/ticket/");
		sb.append(ticketString);

		DefaultHttpClientAndContext clientAndContext = RestAuthUtils
				.httpClientSetup(irodsAccount, testingProperties);
		try {

			HttpDelete httpdelete = new HttpDelete(sb.toString());

			HttpResponse response = clientAndContext.getHttpClient().execute(
					httpdelete, clientAndContext.getHttpContext());
			Assert.assertEquals(204, response.getStatusLine().getStatusCode());
			
			// test that the ticket has been deleted - should throw DataNotFoundException
			Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);

		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			clientAndContext.getHttpClient().getConnectionManager().shutdown();
		}
	}
	
	@Test
	public void updateTicket() throws Exception {
	
		// create a ticket that we will update
		String ticketString = new TicketRandomString(15).nextString();
		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();
		IRODSFile file = accessObjectFactory.getIRODSFileFactory(irodsAccount).instanceIRODSFile(targetIrodsFile);
		ticketService.createTicket(TicketCreateModeEnum.READ, file, ticketString);

		DefaultHttpClientAndContext clientAndContext = null;
		try {
			
			for (String key : restrictionTypeValueMap.keySet()) {
				
				System.out.println("This ran!!!");
				
				String restrictionType = key;
				String restrictionValue = restrictionTypeValueMap.get(key);
				
				System.out.println("****** restrictionType=" + restrictionType + " restrictionValue=" + restrictionValue + " *******");
		
				String requestBody = "<ns2:ticket xmlns:ns2=\"http://irods.org/irods-rest\">"
						+ "<restriction_type>" + restrictionType + "</restriction_type>"
						+ "<restriction_value>" + restrictionValue + "</restriction_value>"
						+ "</ns2:ticket>";

				// Send REST request to update the ticket
				StringBuilder sb = new StringBuilder();
				sb.append("http://localhost:");
				sb.append(testingPropertiesHelper.getPropertyValueAsInt(
						testingProperties, RestTestingProperties.REST_PORT_PROPERTY));
				sb.append("/ticket/");
				sb.append(ticketString);

				clientAndContext = RestAuthUtils
						.httpClientSetup(irodsAccount, testingProperties);

				HttpPut httpput = new HttpPut(sb.toString());
				httpput.addHeader("Content-Type", "application/xml");
			
				HttpEntity requestEntity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
				httpput.setEntity(requestEntity);

				HttpResponse response = clientAndContext.getHttpClient().execute(
					httpput, clientAndContext.getHttpContext());
				Assert.assertEquals(204, response.getStatusLine().getStatusCode());
			
				// check that the request did what we expected
				Ticket t = ticketService.getTicketForSpecifiedTicketString(ticketString);
	
				List<String> tempList;
				switch (restrictionType) {
				case "add_host":
					tempList = ticketService.listAllHostRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Add host expected size of 1 but got size of " + tempList.size(), tempList.size() == 1);
					break;
				case "remove_host":
					tempList = ticketService.listAllHostRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Remove host expected size of 0 but got size of " + tempList.size(), tempList.size() == 0);
					break;
				case "add_group":
					tempList = ticketService.listAllGroupRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Add group expected size of 1 but got size of " + tempList.size(), tempList.size() == 1);
					Assert.assertTrue("Did not get the expected group for ticket.  " + tempList.get(0), restrictionValue.equalsIgnoreCase(tempList.get(0)));
					break;
				case "remove_group":
					tempList = ticketService.listAllGroupRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Remove group expected size of 0 but got size of " + tempList.size(), tempList.size() == 0);
					break;
				case "add_user":
					tempList = ticketService.listAllUserRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Add user expected size of 1 but got size of " + tempList.size(), tempList.size() == 1);
					Assert.assertTrue("Did not get the expected user for ticket.  " + tempList.get(0), restrictionValue.equalsIgnoreCase(tempList.get(0)));
					break;
				case "remove_user":
					tempList = ticketService.listAllUserRestrictionsForSpecifiedTicket(t.getTicketString(), 0);
					Assert.assertTrue("Remove user expected size of 0 but got size of " + tempList.size(), tempList.size() == 0);
					break;
				case "byte_write_limit":
					Assert.assertTrue("Did not get the expected write byte limit for ticket.  " + t.getWriteByteLimit(),  t.getWriteByteLimit() == Long.parseLong(restrictionValue));
					break;
				case "file_write_limit":
					Assert.assertTrue("Did not get the expected write file limit for ticket.  " + t.getWriteFileLimit(), t.getWriteFileLimit() == Long.parseLong(restrictionValue));
					break;
				case "uses_limit":
					Assert.assertTrue("Did not get the expected uses limit for ticket.  " + t.getUsesLimit(), t.getUsesLimit() == Long.parseLong(restrictionValue));
					break;
				case "expiration":		
					Assert.assertTrue("Did not get the expected expiration data.", t.getExpireTime().equals(EXPIRATION_DATE_FORMAT.parse(restrictionValue)));
					break;

				}
			}
		} finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			if (clientAndContext != null)
				clientAndContext.getHttpClient().getConnectionManager().shutdown();
			
			// clean up - delete the ticket
			ticketService.deleteTicket(ticketString);
		}
	}
	

}
