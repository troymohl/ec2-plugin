package hudson.plugins.ec2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import antlr.ANTLRException;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class InstanceStopTimerTest {

    private static final String NODE_LABEL = "test_node";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private AmazonEC2Cloud testCloud;

    @Before
    public void init() throws Exception {
        testCloud = getMockCloud();
        r.jenkins.clouds.add(testCloud);
        Node node = getNode();
        r.jenkins.setNodes(Collections.singletonList(node));
    }

    @Test
    public void testPendingShouldNotBeStopped() throws IOException, InterruptedException, ANTLRException {
        Computer computer = mock(Computer.class);
        when(computer.isConnecting()).thenReturn(false);
        when(computer.isOffline()).thenReturn(true);
        TestableStopTimer stopTimer = new TestableStopTimer(computer, false);
        stopTimer.execute(null);
        verify(testCloud, times(0)).stopNode(any());
    }

    @Test
    public void testIdleNodeShouldBeStopped() throws IOException, InterruptedException, ANTLRException {
        Computer computer = mock(Computer.class);
        when(computer.isConnecting()).thenReturn(false);
        when(computer.isOffline()).thenReturn(true);
        TestableStopTimer stopTimer = new TestableStopTimer(computer, true);
        stopTimer.execute(null);
        verify(testCloud, times(1)).stopNode(any());
    }

    @Test
    public void testNoComputer() throws IOException, InterruptedException {
        TestableStopTimer stopTimer = new TestableStopTimer(null, false);
        stopTimer.execute(null);
        verify(testCloud, times(0)).stopNode(any());
    }

    @Test
    public void testNodeIsConnecting() throws IOException, InterruptedException {
        Computer computer = mock(Computer.class);
        when(computer.isConnecting()).thenReturn(true);
        when(computer.isOnline()).thenReturn(false);
        TestableStopTimer stopTimer = new TestableStopTimer(computer, false);
        stopTimer.execute(null);
        verify(testCloud, times(0)).stopNode(any());
    }

    @Test
    public void testNonIdleNodeShouldNotStop() throws IOException, InterruptedException {
        Computer computer = mock(Computer.class);
        when(computer.isConnecting()).thenReturn(false);
        Executor executor = mock(Executor.class);
        when(executor.isIdle()).thenReturn(false);
        when(executor.getIdleStartMilliseconds()).thenReturn(System.currentTimeMillis());
        when(computer.getAllExecutors()).thenReturn(Collections.singletonList(executor));
        TestableStopTimer stopTimer = new TestableStopTimer(computer, false);
        stopTimer.execute(null);
        verify(testCloud, times(0)).stopNode(any());
    }

    private Node getNode() {
        Node node = mock(Node.class);
        when(node.getNodeName()).thenReturn("Test Node");
        Set<LabelAtom> labelSet = new HashSet<>();
        LabelAtom atom = new LabelAtom(NODE_LABEL);
        labelSet.add(atom);
        when(node.getAssignedLabels()).thenReturn(labelSet);
        return node;
    }

    private AmazonEC2Cloud getMockCloud() {
        AmazonEC2Cloud cloud = mock(AmazonEC2Cloud.class);
        when(cloud.isStartStopNodes()).thenReturn(true);
        when(cloud.isEc2Node(any())).thenReturn(true);
        return cloud;
    }

    private static class TestableStopTimer extends InstanceStopTimer {
        private Computer computer;
        private boolean queueEmpty;

        public TestableStopTimer(Computer testComputer, boolean queueEmpty) {
            computer = testComputer;
            this.queueEmpty = queueEmpty;
        }

        @Override
        protected Computer getComputer(Node node) {
            return computer;
        }

        @Override
        protected Item[] getItems() {
            if (queueEmpty) {
                return new Item[0];
            }

            Item[] items = new Item[1];
            Item item = mock(Item.class);
            try {
                when(item.getAssignedLabel()).thenReturn(Label.parseExpression(NODE_LABEL));
            } catch ( ANTLRException e ) {
                e.printStackTrace();
            }
            items[0] = item;
            return items;
        }
    }
}
