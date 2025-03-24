package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

@SpringBootApplication
public class DemoApplication {

//	public static void main(String[] args) {
//		SpringApplication.run(DemoApplication.class, args);
//
//		System.out.println("Hello World!");
//
//
//	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(DemoApplication.class, args);
		// Specify input and output files
//		File inputFile = new File("test-resultsTestNg.xml");
//		File outputFile = new File("junit-resultsTestNg2.xml");

		File inputFile = new File("test-resultsNunit.xml");
		File outputFile = new File("junit-resultsNunit2.xml");

// Parse the input XML
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document inputDoc = builder.parse(inputFile);

		// Determine input format (TestNG or NUnit)
		String rootElementName = inputDoc.getDocumentElement().getNodeName();
		if ("testng-results".equalsIgnoreCase(rootElementName)) {
			System.out.println("Detected TestNG results. Converting to JUnit...");
			convertTestNGToJUnit(inputDoc, outputFile);
		} else if ("test-results".equalsIgnoreCase(rootElementName)) {
			System.out.println("Detected NUnit results. Converting to JUnit...");
			convertNUnitToJUnit(inputDoc, outputFile);
		} else {
			System.out.println("Unknown format: " + rootElementName);
		}
	}

	private static void convertTestNGToJUnit(Document inputDoc, File outputFile) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document junitDoc = builder.newDocument();

		// Create root <testsuite> element
		Element rootSuite = (Element) inputDoc.getElementsByTagName("suite").item(0);
		Element testsuite = junitDoc.createElement("testsuite");
		String suiteName = rootSuite.getAttribute("name");

		int totalTests = 0;
		int totalFailures = 0;
		int totalSkipped = 0;
		double totalTime = 0.0;

		NodeList tests = inputDoc.getElementsByTagName("test");
		for (int i = 0; i < tests.getLength(); i++) {
			Element test = (Element) tests.item(i);

			NodeList classes = test.getElementsByTagName("class");
			for (int j = 0; j < classes.getLength(); j++) {
				Element clazz = (Element) classes.item(j);

				NodeList testMethods = clazz.getElementsByTagName("test-method");
				for (int k = 0; k < testMethods.getLength(); k++) {
					totalTests++;
					Element testMethod = (Element) testMethods.item(k);
					String methodName = testMethod.getAttribute("name");
					String status = testMethod.getAttribute("status");
					String duration = testMethod.getAttribute("duration-ms");
					totalTime += Double.parseDouble(duration) / 1000;

					Element testcase = junitDoc.createElement("testcase");
					testcase.setAttribute("classname", clazz.getAttribute("name"));
					testcase.setAttribute("name", methodName);
					testcase.setAttribute("time", String.valueOf(Double.parseDouble(duration) / 1000));

					// Extract <exception> details for failures
					NodeList exceptions = testMethod.getElementsByTagName("exception");
					String failureMessage = exceptions.getLength() > 0
							? exceptions.item(0).getTextContent()
							: "No details available.";

					// Extract <skipped> reason for skipped tests
					NodeList skippedReasons = testMethod.getElementsByTagName("skipped");
					String skipMessage = skippedReasons.getLength() > 0
							? skippedReasons.item(0).getTextContent()
							: "No details available.";

					switch (status.toUpperCase()) {
						case "PASS":
							// No additional tags for passed tests
							break;
						case "FAIL":
							totalFailures++;
							Element failure = junitDoc.createElement("failure");
							failure.setAttribute("message", failureMessage);
							failure.setTextContent(failureMessage);
							testcase.appendChild(failure);
							break;
						case "SKIP":
							totalSkipped++;
							Element skipped = junitDoc.createElement("skipped");
							skipped.setAttribute("message", skipMessage);
							skipped.setTextContent(skipMessage);
							testcase.appendChild(skipped);
							break;
						default:
							Element unknown = junitDoc.createElement("unknown");
							unknown.setTextContent("Unhandled status: " + status);
							testcase.appendChild(unknown);
							break;
					}

					testsuite.appendChild(testcase);
				}
			}
		}

		testsuite.setAttribute("name", suiteName);
		testsuite.setAttribute("tests", String.valueOf(totalTests));
		testsuite.setAttribute("failures", String.valueOf(totalFailures));
		testsuite.setAttribute("skipped", String.valueOf(totalSkipped));
		testsuite.setAttribute("time", String.valueOf(totalTime));
		junitDoc.appendChild(testsuite);

		writeXmlToFile(junitDoc, outputFile);
	}

	private static void convertNUnitToJUnit(Document inputDoc, File outputFile) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document junitDoc = builder.newDocument();

		Element rootSuite = (Element) inputDoc.getElementsByTagName("test-suite").item(0);
		Element testsuite = junitDoc.createElement("testsuite");
		String suiteName = rootSuite.getAttribute("name");

		int totalTests = 0;
		int totalFailures = 0;
		int totalSkipped = 0;
		double totalTime = 0.0;

		NodeList testCases = inputDoc.getElementsByTagName("test-case");
		for (int i = 0; i < testCases.getLength(); i++) {
			totalTests++;
			Element testCase = (Element) testCases.item(i);
			String name = testCase.getAttribute("name");
			String result = testCase.getAttribute("result");
			String duration = testCase.getAttribute("duration");
			totalTime += Double.parseDouble(duration);

			Element testcase = junitDoc.createElement("testcase");
			testcase.setAttribute("classname", suiteName); // Use suite name for classname simplicity
			testcase.setAttribute("name", name);
			testcase.setAttribute("time", duration);

			// Extract failure and skipped messages
			String failureMessage = testCase.getElementsByTagName("message").getLength() > 0
					? testCase.getElementsByTagName("message").item(0).getTextContent()
					: "No details available.";

			switch (result.toUpperCase()) {
				case "SUCCESS":
					// No additional tags for successful tests
					break;
				case "FAILURE":
					totalFailures++;
					Element failure = junitDoc.createElement("failure");
					failure.setAttribute("message", failureMessage);
					failure.setTextContent(failureMessage);
					testcase.appendChild(failure);
					break;
				case "SKIPPED":
					totalSkipped++;
					Element skipped = junitDoc.createElement("skipped");
					skipped.setAttribute("message", "Test skipped.");
					skipped.setTextContent("No details provided.");
					testcase.appendChild(skipped);
					break;
				default:
					Element unknown = junitDoc.createElement("unknown");
					unknown.setTextContent("Unhandled result: " + result);
					testcase.appendChild(unknown);
					break;
			}

			testsuite.appendChild(testcase);
		}

		testsuite.setAttribute("name", suiteName);
		testsuite.setAttribute("tests", String.valueOf(totalTests));
		testsuite.setAttribute("failures", String.valueOf(totalFailures));
		testsuite.setAttribute("skipped", String.valueOf(totalSkipped));
		testsuite.setAttribute("time", String.valueOf(totalTime));
		junitDoc.appendChild(testsuite);

		writeXmlToFile(junitDoc, outputFile);
	}

	private static void writeXmlToFile(Document doc, File file) throws Exception {
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(file);
		transformer.transform(source, result);
		System.out.println("Conversion complete! JUnit results saved to: " + file.getAbsolutePath());
	}
}
