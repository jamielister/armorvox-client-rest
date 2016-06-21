package net.auraya;


import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONObject;

public class ArmorvoxClient {
	
	static {
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "warn");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "warn");
	//	System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "debug");
		
		
		
		//System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	private AtomicInteger requestNumber = new AtomicInteger(0);
	private HttpClientBuilder	builder	= null;
	private static final String[] fullApis = {"create_identity", "delete_identity", "retrieve_identity", "update_identity", "list_identities", "generate_phrases", "create_voice_print", "delete_voice_print", "verify_voice_print", "list_voice_prints", "list_verifications", "list_enrolments", "verify_list", "retrieve_audio", "delete_audio", "characterise_audio"};
	private static final String[] shortApis = {"ci", "di", "ri", "ui", "li", "gp", "cvp", "dvp", "vvp", "lvp", "lv", "le", "vl", "ra", "da", "ca"};
	private static final boolean[] requiredArray = {true, true, true, true, false, true, true, true, true, true, false, false, true, false, false, false};
	
	Map<String,String> map = new HashMap<>();
	
	public static void main(String[] args) {
		
		ArmorvoxClient client = new ArmorvoxClient();
		client.parseCommandLine(args);
		client.run();
	}
	
	@AllArgsConstructor
	static class ID {
		String id;
		String utteranceFileString;
		String utteranceFileString2;
		String following;
		
		@Override
		public String toString() {
			return id;
		}
	}
	
	private String getUrl(String api) {
		final String[] servers = StringUtils.split(map.get("server"), ",");
		final String server = servers[requestNumber.incrementAndGet()%servers.length];
		final String urlString = String.format("%s%s%s", server, StringUtils.endsWith(server, "/")?"":"/", api);
		//System.out.println(urlString);
		return urlString;
	}
	
	@SneakyThrows
	private void run() {
		long startTime = System.currentTimeMillis();
		
		final String api = map.get("api");
		final String type = map.get("type");
			
		
		if (!Boolean.valueOf(map.get("list_required"))) {
			try {
				JSONObject response = new JSONObject();
				String parameters = map.getOrDefault("parameters", "");
				if (!StringUtils.isEmpty(parameters)) {
					parameters = "?"+parameters;
				} 
				
				switch (api) {
				case "list_identities":
					response = send("identities", false);
					break;
				case "retrieve_audio":
					String utterance = map.get("utterance");
					response = send("audio/"+utterance, false);
					if (response.has("audio")) {
						// this is just to manage printing of the audio (make it much shorter)
						// normally audio would be left alone
						response.put("audio", StringUtils.substring(response.getString("audio"), 0, 100)+"...");
					}
					break;
				case "delete_audio":
					utterance = map.get("utterance");
					response = send("audio/"+utterance, true);
					break;
				case "list_verifications":
					response = send("verifications/"+type+parameters, false);
					break;
				case "list_enrolments":
					response = send("enrolments/"+type+parameters, false);
					break;
				}
				

				System.out.println(response.toString(3));
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			
			InputStream is = map.get("list") != null?new FileInputStream(map.get("list")):new ByteArrayInputStream("dummy_line".getBytes());
			final List<ID> list = IOUtils.readLines(is).stream().map(line -> {
				String[] parts = StringUtils.split(line); 
				String utteranceFileString = parts.length > 1?parts[1]:null;
				String utteranceFileString2 = parts.length > 2?parts[2]:null;
				String following = StringUtils.removeStart(line, parts[0]);
				return new ID(parts[0], utteranceFileString, utteranceFileString2, StringUtils.isEmpty(following)?"{}":following);
			}).collect(toList());
			
			final File listParent = map.get("list")!=null?new File(FilenameUtils.concat(System.getProperty("user.dir"), map.get("list"))).getParentFile():null;
		
			if (StringUtils.equals(api, "verify_list")) {
				try {
					String utterance = map.get("utterance");
					utterance = FilenameUtils.concat(listParent.getAbsolutePath(), utterance);
					
					FileInputStream fis = new FileInputStream(utterance);
					String base64 = Base64.encodeBase64String(IOUtils.toByteArray(fis));
					JSONObject data = new JSONObject();
					data.put("path", utterance);
					String prefix = map.get("prefix");
					
					JSONArray listArray = new JSONArray(list.stream().map(id -> prefix+id.id).collect(toList()));
					JSONObject json = new JSONObject();
					json.put("utterance", base64);
					json.put("data", data);
					json.put("list", listArray);
					
					JSONObject response = sendEntity("list/"+type, new StringEntity(json.toString(), ContentType.APPLICATION_JSON), true);
					System.out.println(response.toString(3));
				} catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
			
			ForkJoinPool mainPool = new ForkJoinPool();
			if (map.containsKey("pa")) {
				mainPool = new ForkJoinPool(Integer.parseInt(map.get("pa")));
			}
		
			mainPool.submit(() -> {
				list.parallelStream().forEach(line -> {
	
					boolean done = true;
					do {
						done = true;
						String userId = line.id;
						String userIdP = map.get("prefix")+userId;
						StringBuilder sb = new StringBuilder("userId "+ userIdP);
						
						try {
							//MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
							JSONObject json = new JSONObject();
							JSONObject response = null;
							
							String parameters = map.getOrDefault("parameters", "");
							if (!StringUtils.isEmpty(parameters)) {
								parameters = "?"+parameters;
							} 
							
							switch (api) {
							case "create_identity":
								json = new JSONObject(line.following);
								response = sendEntity("identities/"+userIdP, new StringEntity(json.toString(), ContentType.APPLICATION_JSON), false);
								
								break;
							case "delete_identity":
								response = send("identities/"+userIdP, true);
								break;
							case "retrieve_identity":
								response = send("identities/"+userIdP, false);
								break;
							case "generate_phrases":
								response = send("identities/"+userIdP+"/phrases"+parameters, false);
								break;
							case "create_voice_print":
								json = new JSONObject(line.following);
	
								JSONArray base64Utterances = new JSONArray();
								if (json.has("utterances")) {
									// assume these are file paths, convert to Base64
									JSONArray utterances = json.getJSONArray("utterances");
									for (int i = 0 ; i < utterances.length(); i++) {
										FileInputStream fis = new FileInputStream(new File(listParent, utterances.getString(i)));
										String base64 = Base64.encodeBase64String(IOUtils.toByteArray(fis));
										base64Utterances.put(base64);
									}
								} else {
									// otherwise need to find audio in default locations (ID/ID-1-type-N.wav)
									for (int i = 1; i <= 3; i++) {
										String filename = String.format("%s/%s-1-%s-%d.wav",  userIdP, userIdP,type,i);
										FileInputStream fis = new FileInputStream(new File(listParent, filename));
										String base64 = Base64.encodeBase64String(IOUtils.toByteArray(fis));
										base64Utterances.put(base64);
									}
								}
								
									
								
								json.put("utterances", base64Utterances);
								response = sendEntity("identities/"+line.id+"/types/"+type, new StringEntity(json.toString(), ContentType.APPLICATION_JSON), false);
								break;
							case "delete_voice_print":
								response = send("identities/"+line.id+"/types/"+type, true);
								break;
							case "verify_voice_print":
								json = new JSONObject(line.following);
	
								String base64 = "";
								if (json.has("utterance")) {
									// assume these are file paths, convert to Base64
									FileInputStream fis = new FileInputStream(new File(listParent, json.getString("utterance")));
									base64 = Base64.encodeBase64String(IOUtils.toByteArray(fis));
								} else {
									// otherwise need to find audio in default locations (ID/ID-1-type-N.wav)
									String filename = String.format("%s/%s-2-%s-%d.wav", userIdP, userIdP, type, 1);
									FileInputStream fis = new FileInputStream(new File(listParent, filename));
									base64 = Base64.encodeBase64String(IOUtils.toByteArray(fis));
								}
								
								json.put("utterance", base64);
								response = sendEntity("identities/"+line.id+"/types/"+type, new StringEntity(json.toString(), ContentType.APPLICATION_JSON), true);
								break;
							case "list_voice_prints":
								response = send("identities/"+line.id+"/types", false);
								break;
							}
							
							System.out.println(response.toString(3));
			
						} catch (Exception e) {
							e.printStackTrace();
							append(sb,"Exception: ");
							append(sb,e.getLocalizedMessage());
							
						} finally {
							String message = sb.toString();
							if (message.contains("Verification in Progress")) {
								done = false;
								message += " Retrying...";
							}
							synchronized(System.out) {
								System.out.println(message);
								System.out.flush();
							}
						}
					} while (!done);
					
				});
			}).get();
			
		}
		

		
		long endTime = System.currentTimeMillis();
		System.out.println("Total time = " + (endTime - startTime));
	}
	
	private void append(StringBuilder sb, String text) {
		sb.append(" ");
		sb.append(text);
	}

	
	@SneakyThrows
	private JSONObject sendEntity(String urlString, HttpEntity clientResponseEntity, boolean isPut) {
		urlString = getUrl(urlString);
		HttpEntityEnclosingRequestBase request = isPut?new HttpPut(urlString):new HttpPost(urlString);
		request.setHeader("User-Agent", "JavaArmorvoxClient");
		request.setHeader("Authorization", map.get("key"));
		request.setEntity(clientResponseEntity);
		
		//clientResponseEntity.writeTo(System.out);
		
		final HttpClient client = builder.build(); 
		
		final HttpResponse serverResponse = client.execute(request);
		String response = IOUtils.toString(serverResponse.getEntity().getContent());
		//System.out.println(response);
		return new JSONObject(response);
	}
	
	@SneakyThrows
	private JSONObject send(String urlString, boolean isDelete) {
		urlString = getUrl(urlString);
		HttpRequestBase request = isDelete?new HttpDelete(urlString):new HttpGet(urlString);
		request.setHeader("User-Agent", "JavaArmorvoxClient");
		request.setHeader("Authorization", map.get("key"));
		
		//clientResponseEntity.writeTo(System.out);
		
		final HttpClient client = builder.build(); 
		
		final HttpResponse serverResponse = client.execute(request);
		return new JSONObject(IOUtils.toString(serverResponse.getEntity().getContent()));
	}
	
	
	
	@SneakyThrows
	private HttpClientBuilder getClientBuilder() {
		HttpClientBuilder builder = HttpClientBuilder.create();

		
		RegistryBuilder<ConnectionSocketFactory> csf = RegistryBuilder.<ConnectionSocketFactory> create();
		
			
		KeyManager[] keyStoreManagers = null;
		if (map.get("keystore") != null) {
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			
			KeyStore keyStore = KeyStore.getInstance("jks");
			
			//System.out.printf("keystore=%s keystorePassword=%s%n",map.get("keystore"), map.get("keystorePassword"));
			keyStore.load(new FileInputStream(map.get("keystore")),  null);
			kmf.init(keyStore, map.get("keystorePassword").toCharArray());
			keyStoreManagers = kmf.getKeyManagers();
		}  
		
		

		TrustManager[] trustStoreManagers = null;
		if (map.get("truststore") != null) {
			TrustManagerFactory tmf =  TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			
			KeyStore trustStore = KeyStore.getInstance("jks");
			//System.out.printf("truststore=%s truststorePassword=%s%n",map.get("truststore"), map.get("truststorePassword"));
			
			trustStore.load(new FileInputStream(map.get("truststore")), null);
			tmf.init(trustStore);
			trustStoreManagers = tmf.getTrustManagers();
		}
		
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyStoreManagers, trustStoreManagers, null);
		
		// SSL certificate will NOT check the host name matches!
		// Remove "(hostname, session) -> true" in production code!
		SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(sslContext, (hostname, session) -> true);
		csf.register("https", scsf);
		
		csf.register("http", PlainConnectionSocketFactory.getSocketFactory());
		
		
		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(csf.build());
		connManager.setDefaultMaxPerRoute(16); // default is 2 which too low - should be at least the number of threads and probably more.
		builder.setConnectionManager(connManager);
		return builder;
	}
	
	
	@SneakyThrows
	void parseCommandLine(String[] args) {
		CommandLineParser cmdParser = new DefaultParser();

		Options options = new Options();
		
		options.addOption("l", "list", true, "File list. Each line contains ID followed by optional JSON payload. Utterance paths in the payload will be converted to Base64 binary.");
		options.addOption("t", "type", true, "Item type, default is 1. Can be comma separated for text_independent_enrol");

		options.addOption("s", "server", true, "scheme, address or name of Armorvox server(s) and port. default is 'https://cloud.armorvox.com/eval_server/json/v1/'");
		options.addOption("a", "api", true, "API to use (can be acronym e.g, 'ae'). Default is auraya_enrol. Choose from: " + StringUtils.join(fullApis, ",") +"," + StringUtils.join(shortApis, ","));
		
		options.addOption("k", "key", true, "Key file to use. default is EVAL-ARMORVOX-CLIENT-LIC");
		options.addOption("p", "parameters", true, "Request parameters. Format is key1=value1&key2=value2");
		
		

		options.addOption("u", "utterance", true, "For 'verify_list' it is relative to list file (or absolute). Can also be used for 'verify_voice_print' - usual default is 'ID/ID-2-type-1.wav'. For 'retrieve_audio' it is the audio_id");


		options.addOption("co", "connect_timeout", true, "Connect timeout to server in milliseconds. Default is 60000");
		options.addOption("so", "socket_timeout", true, "Socket timeout to server in milliseconds. Default is 60000");
		options.addOption("pa", "parallelism", true, "Number of client parallel threads");

		options.addOption("ts", "truststore", true, "Path to truststore file (trusted server certificates). Use with keystore to enable https SSL/TLS mode. Leave blank to use Java's default trusted credentials.");
		options.addOption("ks", "keystore", true, "Path to keystore file (private credentials). Use with truststore to enable https SSL/TLS mode.");
		options.addOption("ksp", "keystorePassword", true, "Keystore password. Default is 'default'");
		
		options.addOption("pr", "prefix", true, "Prefix for ID. Facilitates performance testing with many identical enrolments.");

		
		try {
			CommandLine cmd = cmdParser.parse(options, args);
			
			boolean listRequired = true;
			
			
			if (cmd.hasOption('s')) {
				map.put("server",cmd.getOptionValue('s'));
			} else {
				map.put("server","https://cloud.armorvox.com/eval_server/json/v1/");
			}

			

			if (cmd.hasOption('a')) {
				String api = cmd.getOptionValue('a');
				
				int shortIndex = Arrays.asList(shortApis).indexOf(api.toLowerCase());
				api = shortIndex >= 0 ? fullApis[shortIndex]:api;
				int index = Arrays.asList(fullApis).indexOf(api);
				if (index < 0) {
					throw new RuntimeException("Unknown API ["+api+"]");
				}
				
				listRequired = requiredArray[index];
				
				map.put("api", api);
			} else {
				map.put("api","create_identity");
			}
			
			if (cmd.hasOption('k')) {
				map.put("key",cmd.getOptionValue('k'));
			} else {
				map.put("key","EVAL-ARMORVOX-CLIENT-LIC");
			}
			
			if (cmd.hasOption('v')) {
				map.put("vocab",cmd.getOptionValue('v'));
			} else if (map.get("api").startsWith("text_prompted_")) {
				throw new RuntimeException("Text prompted API requires 'vocab' option");
			}
			
			map.put("prefix", cmd.getOptionValue("pr", ""));
			map.put("list_required", Boolean.toString(listRequired));
				
			
			if (cmd.hasOption('m')) {
				map.put("mode",cmd.getOptionValue('m'));
			} else if (map.get("api").equals("text_prompted_getPhrase")) {
				throw new RuntimeException("Text prompted getPhrase API requires 'mode' option");
			}
			
			if (cmd.hasOption('n')) {
				map.put("n",cmd.getOptionValue('n'));
			} else {
				map.put("n","10");
			}

			if (cmd.hasOption('h')) {
				map.put("threshold",cmd.getOptionValue('h'));
			} else {
				map.put("threshold","-1");
			}
			
			if (cmd.hasOption('u')) {
				map.put("utterance",cmd.getOptionValue('u'));
			} else if (map.get("api").equals("verify_list")) {
				throw new RuntimeException("verify_list API requires 'utterance' option");
			}
			

			if (cmd.hasOption('t')) {
				map.put("type",cmd.getOptionValue('t'));
			} else {
				map.put("type","1");
			}
			
			if (cmd.hasOption("pa")) {
				map.put("pa",cmd.getOptionValue("pa"));
			} 
			
			if (cmd.hasOption("p")) {
				map.put("parameters",cmd.getOptionValue("p"));
			} 
			
			
			
			if (cmd.hasOption("ks")) {
				map.put("keystore", cmd.getOptionValue("ks"));
				map.put("keystorePassword", cmd.getOptionValue("ksp", "default"));
			}
			
			if (cmd.hasOption("ts")) {
				map.put("truststore", cmd.getOptionValue("ts"));
			}
			

			if (cmd.hasOption('l')) {
				map.put("list",cmd.getOptionValue('l'));
			} else {
				if (listRequired) {
					throw new RuntimeException("Missing required option l");
				}
			}
		

			RequestConfig.Builder requestBuilder = RequestConfig.custom()
			//	.setStaleConnectionCheckEnabled(true) // deprecated
				.setConnectTimeout(10000)
				.setSocketTimeout(10000);
			if (cmd.hasOption("so")) {
				requestBuilder = requestBuilder.setSocketTimeout(Integer.valueOf(cmd.getOptionValue("so")));
			}
			if (cmd.hasOption("co")) {
				requestBuilder = requestBuilder.setConnectTimeout(Integer.valueOf(cmd.getOptionValue("co")));
			}
			
			
			builder	= getClientBuilder();
			builder.setDefaultRequestConfig(requestBuilder.build());
			
		} catch (Exception e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.setOptionComparator(null);
			formatter.printHelp("java -jar armorvox-client.jar ", options, true);
			System.exit(0);
		}
	}
	
	static <T> Stream<T> stream(Collection<T> collection, boolean isParallel) {
		if (isParallel) {
			return collection.parallelStream();
		} else {
			return collection.stream();
		}
	}

}
