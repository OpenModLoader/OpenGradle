/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import com.google.gson.Gson;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.IdeaRunConfig;
import net.fabricmc.loom.util.Version;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GenIdeaProjectTask extends DefaultTask {

	@TaskAction
	public void genIdeaRuns() throws IOException, ParserConfigurationException, SAXException, TransformerException {
		Project project = this.getProject();
		LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
		project.getLogger().lifecycle(":Building idea workspace");

		File file = new File(project.getName() + ".iml");
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(file);

		Node component = null;
		NodeList module = doc.getElementsByTagName("module").item(0).getChildNodes();
		for (int i = 0; i < module.getLength(); i++) {
			if (module.item(i).getNodeName().equals("component")) {
				component = module.item(i);
				break;
			}
		}

		if (component == null) {
			project.getLogger().lifecycle(":failed to generate intellij run configurations");
			return;
		}

		Node content = null;
		NodeList moduleList = component.getChildNodes();

		for (int i = 0; i < moduleList.getLength(); i++) {
			if (moduleList.item(i).getNodeName().equals("content")) {
				content = moduleList.item(i);
			}
		}

		if (content == null) {
			project.getLogger().lifecycle(":failed to generate intellij run configurations");
			return;
		}

		Element sourceFolder = doc.createElement("sourceFolder");
		sourceFolder.setAttribute("url", "file://$MODULE_DIR$/minecraft/src/main/java");
		sourceFolder.setAttribute("isTestSource", "false");
		content.appendChild(sourceFolder);

		sourceFolder = doc.createElement("sourceFolder");
		sourceFolder.setAttribute("url", "file://$MODULE_DIR$/minecraft/src/main/resources");
		sourceFolder.setAttribute("type", "java-resource");
		content.appendChild(sourceFolder);

		Gson gson = new Gson();

		Version version = gson.fromJson(new FileReader(Constants.MINECRAFT_JSON.get(extension)), Version.class);

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(file);
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(source, result);

		file = new File(project.getName() + ".iws");
		docFactory = DocumentBuilderFactory.newInstance();
		docBuilder = docFactory.newDocumentBuilder();
		doc = docBuilder.parse(file);

		NodeList list = doc.getElementsByTagName("component");
		Element runManager = null;
		for (int i = 0; i < list.getLength(); i++) {
			Element element = (Element) list.item(i);
			if (element.getAttribute("name").equals("RunManager")) {
				runManager = element;
				break;
			}
		}

		if (runManager == null) {
			project.getLogger().lifecycle(":failed to generate intellij run configurations");
			return;
		}

		IdeaRunConfig ideaClient = new IdeaRunConfig();
		ideaClient.mainClass = "cpw.mods.modlauncher.Launcher";
		ideaClient.projectName = project.getName();
		ideaClient.configName = "Minecraft Client";
		ideaClient.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		ideaClient.vmArgs = "-Djava.library.path=" + Constants.MINECRAFT_NATIVES.get(extension).getAbsolutePath() + " -Doml.development=true";
		ideaClient.programArgs = "--launchTarget omldevclient --accessToken not_a_real_token --version " + extension.version + " --assetIndex " + version.assetIndex.id + " --assetsDir " + new File(extension.getUserCache(), "assets-" + extension.version).getAbsolutePath();

		runManager.appendChild(ideaClient.genRuns(runManager));

		IdeaRunConfig ideaServer = new IdeaRunConfig();
		ideaServer.mainClass = "com.openmodloader.loader.launch.ServerLauncher";
		ideaServer.projectName = project.getName();
		ideaServer.configName = "Minecraft Server";
		ideaServer.runDir = "file://$PROJECT_DIR$/" + extension.runDir;
		ideaServer.vmArgs = "-Doml.development=true";
		ideaServer.programArgs = "";

		runManager.appendChild(ideaServer.genRuns(runManager));

		transformerFactory = TransformerFactory.newInstance();
		transformer = transformerFactory.newTransformer();
		source = new DOMSource(doc);
		result = new StreamResult(file);
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		transformer.transform(source, result);

		File runDir = new File(Constants.WORKING_DIRECTORY, extension.runDir);
		if (!runDir.exists()) {
			runDir.mkdirs();
		}
	}
}
