package hudson.plugins.deploy;

import hudson.model.Result;
import hudson.plugins.deploy.tomcat.Tomcat8xAdapter;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

/**
 * Tests pipeline compatibility. Since there ia no Builder that sets the build status all of these tests
 * will ultimately result in a no-op.
 */
public class PipelineSyntaxTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    /**
     * This builds out the Jenkins pipeline script. The first couple steps attempt to build out a workspace that can be
     * used in the deploy steps. The workspace will have two files in it - readme.txt - a simple text file, and
     * target/app.war - a very small war file to be used in deploy testing.
     *
     * @param func The function to run after building the workspace.
     * @return Returns a string representation of a Jenkins pipeline script.
     */
    private String getFullScript (String func) {
        return "node {\n" +
                    "writeFile(file: 'readme.txt', text: 'this creates a workspace if one does not already exist')\n" +
                    "if (isUnix()) {\n" +
                        "sh 'mkdir target'\n" +
                        "sh 'zip target/app.war readme.txt'\n" +
                    "} else {\n" +
                        "bat 'mkdir target'\n" +
                        "bat 'jar -cMf target/app.war .'\n" +
                    "}\n" +
                    func +
                "}";
    }

    @Test
    public void testNoAdapterDeploy() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "DryRunTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript("deploy(war: 'target/app.war', contextPath: 'app', onFailure: false)"),
                false));
        jenkins.assertBuildStatusSuccess(workflow.scheduleBuild2(0));
    }

    @Test
    public void testMockAdapterDeploy() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "MockTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript("deploy(adapters: [workflowAdapter()], war: 'target/app.war', contextPath: 'app')"),
                false));
        jenkins.assertBuildStatusSuccess(workflow.scheduleBuild2(0));
    }

    @Test
    public void testMockAdaptersDeploy() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "MockTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript("deploy(adapters: [workflowAdapter(), workflowAdapter(), workflowAdapter()], war: 'target/app.war', contextPath: 'app')"),
                false));
        jenkins.assertBuildStatusSuccess(workflow.scheduleBuild2(0));
    }

    // @Test
    public void testGlassFishAdapter() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "GlassfishTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript(
                "def gf2 = glassfish2( " +
                    "home: 'FAKE', " +
                    "credentialsId: 'FAKE'," +
                    "adminPort: '1234', " +
                    "hostname: 'localhost') \n" +
                "def gf3 = glassfish3( " +
                    "home: 'FAKE', " +
                    "credentialsId: 'FAKE'," +
                    "adminPort: '1234', " +
                    "hostname: 'localhost') \n" +
                "deploy(adapters: [gf2, gf3], war: 'target/app.war', contextPath: 'app')"),
                false));

        jenkins.assertBuildStatusSuccess(workflow.scheduleBuild2(0));
    }

    @Test
    public void testTomcatAdapter() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "TomcatTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript(
                "def tc7 = tomcat7( " +
                    "url: 'FAKE', " +
                    "managerContext: '/manager', " +
                    "credentialsId: 'FAKE') \n" +
                "def tc8 = tomcat8( " +
                    "home: 'FAKE', " +
                    "managerContext: '/manager', " +
                    "credentialsId: 'FAKE') \n" +
                "deploy(adapters: [tc7, tc8], war: 'target/app.war', contextPath: 'app')"),
                false));
        jenkins.assertBuildStatusSuccess(workflow.scheduleBuild2(0));
    }

    @Test
    public void testLegacyAdapterThrows() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "legacyTest");
        workflow.setDefinition(new CpsFlowDefinition(
                getFullScript(
                        "writeFile(file: 'target/app.war', text: '')\n" +
                        "deploy(adapters: [legacyAdapter()], war: 'target/app.war', contextPath: 'app', onFailure: true)"),
                false));
        WorkflowRun runResults = workflow.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, runResults);
        jenkins.assertLogContains("Please contact the plugin maintainer", runResults);
    }

    @Test
    public void testSnippetizerDefaults() throws Exception {
        jenkins.getInstance().createProject(WorkflowJob.class, "SnippetTest");
        SnippetizerTester snippetTester = new SnippetizerTester(jenkins);

        ContainerAdapter tomcatAdapter = new Tomcat8xAdapter("http://example.com", "/manager2", "test-id");
        DeployPublisher publisher = new DeployPublisher(Collections.singletonList(tomcatAdapter), "app.war");

        snippetTester.assertRoundTrip(new CoreStep(publisher), "deploy adapters: [tomcat8(credentialsId: 'test-id', managerContext: '/manager2', url: 'http://example.com')], war: 'app.war'");
    }

    @Test
    public void testSnippetizerNonDefault() throws Exception {
        WorkflowJob workflow = jenkins.getInstance().createProject(WorkflowJob.class, "SnippetTest");
        SnippetizerTester snippetTester = new SnippetizerTester(jenkins);

        ContainerAdapter tomcatAdapter = new Tomcat8xAdapter("http://example.com", "/manager2", "test-id");
        DeployPublisher publisher = new DeployPublisher(Collections.singletonList(tomcatAdapter), "app.war");
        publisher.setOnFailure(!jenkins.jenkins.getDescriptorByType(DeployPublisher.DescriptorImpl.class).defaultOnFailure(workflow));
        publisher.setContextPath("my-app");

        snippetTester.assertRoundTrip(new CoreStep(publisher), "deploy adapters: [tomcat8(credentialsId: 'test-id', managerContext: '/manager2', url: 'http://example.com')], contextPath: 'my-app', onFailure: false, war: 'app.war'");
    }

}
