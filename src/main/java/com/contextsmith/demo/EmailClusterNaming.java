package com.contextsmith.demo;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.net.whois.WhoisClient;

import com.google.common.base.Stopwatch;

public class EmailClusterNaming {
	private static final Logger log = LogManager.getLogger(EmailClusterNaming.class);

	private static Pattern server_pattern;
	private static Pattern org_pattern;
	private Matcher server_matcher;
	private Matcher org_matcher;

	// regex whois parser
	private static final String WHOIS_SERVER_PATTERN = "Whois Server:\\s(.*)";
	private static final String ORGANIZATION_PATTERN = "Registrant Organization:\\s(.*)";

	static {
		server_pattern = Pattern.compile(WHOIS_SERVER_PATTERN);
		org_pattern = Pattern.compile(ORGANIZATION_PATTERN);
	}

	private String queryWithWhoisServer(String domainName, String whoisServer) throws SocketException, IOException {

		String result = "";
		WhoisClient whois = new WhoisClient();
		whois.connect(whoisServer);
		result = whois.query(domainName);
		whois.disconnect();

		return result;

	}

	private List<String> getWhoisServer(String whois) {

		List<String> result = new ArrayList<String>();

		server_matcher = server_pattern.matcher(whois);

		// get last whois server
		while (server_matcher.find()) {
			result.add(server_matcher.group(1));
		}
		return result;
	}

	private String getWhoisOrg(String whois) {

		String result = "";

		org_matcher = org_pattern.matcher(whois);

		// get last whois org
		while (org_matcher.find()) {
			result = org_matcher.group(1);
		}
		return result;
	}

	// example google.com
	public String getWhois(String domainName) {

		StringBuilder result = new StringBuilder("");

		WhoisClient whois = new WhoisClient();
		try {

			whois.connect(WhoisClient.DEFAULT_HOST);
			String whoisData1 = whois.query("=" + domainName);
			whois.disconnect();

			// get the whois server
			List<String> whoisServerUrlList = getWhoisServer(whoisData1);
			// result.append(whoisServerUrlList.toString());
			for (String whoisServerUrl : whoisServerUrlList) {
				if (whoisServerUrl.equals("")) {
					continue;
				}

				// whois -h whois.markmonitor.com google.com
				try {
					String whoisData2 = queryWithWhoisServer(domainName, whoisServerUrl);
					String whoisOrg = getWhoisOrg(whoisData2);
					// append 2nd result
					if (!whoisOrg.equals("")) {
						result.append(whoisOrg);
						break;
					}

				} catch (SocketException e) {
					continue;
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result.toString();
	}

	private static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		Map<K, V> result = new LinkedHashMap<>();
		Stream<Entry<K, V>> st = map.entrySet().stream();

		st.sorted(Comparator.comparing(e -> e.getValue())).forEach(e -> result.put(e.getKey(), e.getValue()));

		return result;
	}

	// Return the most frequent domain. If tie, return anyone of them.
	private String getMostRepresentativeDomain(Set<InternetAddress> cluster) {
		Map<String, Integer> domainFrequency = new HashMap<String, Integer>();
		for (InternetAddress address : cluster) {
			String domain = address.getAddress().split("@")[1];
			int count = domainFrequency.containsKey(domain) ? domainFrequency.get(domain) : 0;
			domainFrequency.put(domain, count + 1);
		}
		domainFrequency = sortByValue(domainFrequency);
		return domainFrequency.keySet().iterator().next();
	}

	public List<String> findClusterNames(Set<Set<InternetAddress>> clusters) {
		List<String> result = new ArrayList<String>();
		for (Set<InternetAddress> cluster : clusters) {
			String whoisdata = getWhois(getMostRepresentativeDomain(cluster));
			result.add(whoisdata);
		}
		return result;
	}

	public static void main(String[] args) throws AddressException {
		List<MimeMessage> messages = EmailClustererMain.fetchEmails();

		EmailPeopleManager epm = new EmailPeopleManager();
		epm.loadMessages(messages); // Index messages.
		Stopwatch stopwatch = new Stopwatch();
		stopwatch.start();

		// Predict user's e-mail addresses.
		Map<InternetAddress, Double> addressScoreMap = UserAddressPredictor.predict(messages);

		// We use the user's e-mail address to obtain company's email domain;
		// so it must *not* be empty here.
		if (addressScoreMap.isEmpty())
			return;

		// Extract company mail address domain.
		String internalDomain = EmailClustererUtil.findInternalDomain(addressScoreMap.keySet());

		Set<Set<InternetAddress>> clusters = null;
		if (internalDomain == null) { // A common domain (eg. gmail).
			clusters = EmailClusterer.findClustersIgnoringDomain(messages, addressScoreMap.keySet());
		} else {
			clusters = EmailClusterer.findExternalClusters(messages, addressScoreMap.keySet(), internalDomain);
		}
		if (clusters == null)
			return;
		EmailClusterNaming emailClusterNaming = new EmailClusterNaming();
		List<String> names = emailClusterNaming.findClusterNames(clusters);
		for (String name : names) {
			log.info(String.format("Cluster[%s]", name));
		}
	}

}
