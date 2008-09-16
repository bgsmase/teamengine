/****************************************************************************

 The contents of this file are subject to the Mozilla Public License
 Version 1.1 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at
 http://www.mozilla.org/MPL/

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 the specific language governing rights and limitations under the License.

 The Original Code is TEAM Engine.

 The Initial Developer of the Original Code is Northrop Grumman Corporation
 jointly with The National Technology Alliance.  Portions created by
 Northrop Grumman Corporation are Copyright (C) 2005-2006, Northrop
 Grumman Corporation. All Rights Reserved.

 Contributor(s): No additional contributors to date

 ****************************************************************************/
package com.occamlab.te.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.ClassLoader;
import java.net.URLDecoder;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.occamlab.te.index.SuiteEntry;
import com.occamlab.te.util.DomUtils;

/**
 * Reads the test harness configuration file. The file is structured as follows:
 * 
 * <pre>
 *    &lt;config&gt;
 *      &lt;home&gt;${base-url}&lt;/home&gt;
 *      &lt;usersdir&gt;${users.dir}&lt;/usersdir&gt;
 *      &lt;!-- one or more test suites --&gt;
 *      &lt;sources id=&quot;${test-suite-id}&quot;&gt;
 *        &lt;source&gt;${ctl.source.location}&lt;/source&gt;
 *        &lt;!-- additional CTL source locations --&gt;
 *      &lt;/sources&gt;
 *    &lt;/config&gt;
 * </pre>
 */
public class Config {
    private String home;
    private File scriptsDir;
    private File usersDir;
    private File workDir;
    private List<String> organizationList;
    private Map<String, List<String>> standardMap;  // Key is org, value is a list of standards
    private Map<String, List<String>> versionMap;   // Key is org_std, value is a list of versions
    private Map<String, List<String>> revisionMap;  // Key is org_std_ver, value is a list of revisions
    private Map<String, SuiteEntry> suites;         // Key is org_std_ver_rev, value is a SuiteEntry 
    private Map<String, List<File>> sources;        // Key is org_std_ver_rev, value is a list of sources 

//    private static LinkedHashMap<String, List<File>> availableSuites;

    public Config() {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Document doc = db.parse(cl.getResourceAsStream("config.xml"));
            Element configElem = (Element) (doc.getElementsByTagName("config").item(0));
            Element homeElem = (Element) (configElem.getElementsByTagName("home").item(0));
            home = homeElem.getTextContent();

            Element scriptsDirEl = DomUtils.getElementByTagName(configElem, "scriptsdir");
            scriptsDir = findFile(scriptsDirEl.getTextContent(), cl);
            if (!scriptsDir.isDirectory()) {
                System.out.println("Error: Directory " + scriptsDirEl.getTextContent() + " does not exist.");
            }

            Element usersDirEl = DomUtils.getElementByTagName(configElem, "usersdir");
            usersDir = findFile(usersDirEl.getTextContent(), cl);
            if (!usersDir.isDirectory()) {
                System.out.println("Error: Directory " + usersDirEl.getTextContent() + " does not exist.");
            }

            Element workDirEl = DomUtils.getElementByTagName(configElem, "workdir");
            workDir = findFile(workDirEl.getTextContent(), cl);
            if (!workDir.isDirectory()) {
                System.out.println("Error: Directory " + workDirEl.getTextContent() + " does not exist.");
            }
            
            organizationList = new ArrayList<String>();
            standardMap = new HashMap<String, List<String>>();
            versionMap = new HashMap<String, List<String>>();
            revisionMap = new HashMap<String, List<String>>();
            suites = new HashMap<String, SuiteEntry>(); 
            sources = new HashMap<String, List<File>>(); 

            for (Element organizationEl : DomUtils.getElementsByTagName(configElem, "organization")) {
                String organization = DomUtils.getElementByTagName(organizationEl, "name").getTextContent();
                organizationList.add(organization);

                for (Element standardEl : DomUtils.getElementsByTagName(organizationEl, "standard")) {
                    String standard = DomUtils.getElementByTagName(standardEl, "name").getTextContent();
                    ArrayList<String> standardList = new ArrayList<String>();
                    standardList.add(standard);
                    standardMap.put(organization, standardList);

                    for (Element versionEl : DomUtils.getElementsByTagName(standardEl, "version")) {
                        String version = DomUtils.getElementByTagName(versionEl, "name").getTextContent();
                        ArrayList<String> versionList = new ArrayList<String>();
                        versionList.add(version);
                        String verKey = organization + "_" + standard;
                        versionMap.put(verKey, versionList);

                        for (Element suiteEl : DomUtils.getElementsByTagName(versionEl, "suite")) {
                            String revision = DomUtils.getElementByTagName(suiteEl, "revision").getTextContent();
                            ArrayList<String> revisionList = new ArrayList<String>();
                            revisionList.add(revision);
                            String revKey = verKey + "_" + version;
                            revisionMap.put(revKey, revisionList);
                            
                            String key = revKey + "_" + revision;

                            SuiteEntry suite = new SuiteEntry();
                            String namespaceUri = DomUtils.getElementByTagName(suiteEl, "namespace-uri").getTextContent();
                            String prefix = DomUtils.getElementByTagName(suiteEl, "prefix").getTextContent();
                            String localName = DomUtils.getElementByTagName(suiteEl, "local-name").getTextContent();
                            suite.setQName(new QName(namespaceUri, localName, prefix));
                            suites.put(key, suite);
                            
                            ArrayList<File> list = new ArrayList<File>();
                            for (Element sourceEl : DomUtils.getElementsByTagName(suiteEl, "source")) {
                                list.add(new File(scriptsDir, sourceEl.getTextContent()));
                            }
                            sources.put(key, list);
                        }
                    }
                }
            }

//            File script_dir = new File(URLDecoder.decode(cl.getResource(
//                    "com/occamlab/te/scripts/parsers.ctl").getFile(), "UTF-8")).getParentFile();
//
//            // automatically load extension modules
//            URL modulesURL = cl.getResource("modules/");
//            File modulesDir = null;
//            if (modulesURL != null) {
//                modulesDir = new File(URLDecoder.decode(modulesURL.getFile(), "UTF-8"));
//            }
//
//            availableSuites = new LinkedHashMap<String, List<File>>();
//            NodeList sourcesList = configElem.getElementsByTagName("sources");
//            for (int i = 0; i < sourcesList.getLength(); i++) {
//                ArrayList<File> ctlLocations = new ArrayList<File>();
//                ctlLocations.add(script_dir);
//                if (modulesDir != null) {
//                    ctlLocations.add(modulesDir);
//                }
//                Element sources = (Element) sourcesList.item(i);
//                String id = sources.getAttribute("id");
//                NodeList sourceList = sources.getElementsByTagName("source");
//                for (int j = 0; j < sourceList.getLength(); j++) {
//                    Element source = (Element) sourceList.item(j);
//                    File f = findFile(source.getTextContent(), cl);
//                    if (!f.exists()) {
//                        // TODO: Log this
//                        throw new FileNotFoundException("Source location "
//                                + source.getTextContent() + " does not exist.");
//                    }
//                    ctlLocations.add(f);
//                }
//                availableSuites.put(id, ctlLocations);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getHome() {
        return home;
    }

    public File getScriptsDir() {
        return scriptsDir;
    }

    public File getUsersDir() {
        return usersDir;
    }

    public File getWorkDir() {
        return workDir;
    }

    public List<String> getOrganizationList() {
        return organizationList;
    }

    public Map<String, List<String>> getRevisionMap() {
        return revisionMap;
    }

    public Map<String, List<File>> getSources() {
        return sources;
    }

    public Map<String, List<String>> getStandardMap() {
        return standardMap;
    }

    public Map<String, SuiteEntry> getSuites() {
        return suites;
    }

    public Map<String, List<String>> getVersionMap() {
        return versionMap;
    }

//    public static LinkedHashMap<String, List<File>> getAvailableSuites() {
//        return availableSuites;
//    }
//
    /**
     * Finds a source file or directory. The location may be specified using:
     * <ul>
     * <li>an absolute system path;</li>
     * <li>a path relative to the location identified by the
     * <code>catalina.base</code> system property;</li>
     * <li>a classpath location.</li>
     * </ul>
     */
    private static File findFile(String path, ClassLoader loader) {
        File f = new File(path);
        if (!f.exists()) {
            f = new File(System.getProperty("catalina.base"), path);
        }
        if (!f.exists()) {
            URL url = loader.getResource(path);
            if (null != url) {
                f = new File(url.getFile());
            } else {
                System.out.println("Directory is not accessible: " + path);
            }
        }
        return f;
    }
}
