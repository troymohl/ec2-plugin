package hudson.plugins.ec2;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

@Extension
public class InstanceStopTimer extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(InstanceStopTimer.class.getName());

    private static final long STOP_DISABLED = -1;

    public InstanceStopTimer() {
        super(InstanceStopTimer.class.getName());
    }

    protected InstanceStopTimer(String name) {
        super(name);
    }

    @Override protected void execute(TaskListener taskListener) throws IOException, InterruptedException {
        Jenkins jenkinsInstance = Jenkins.get();
        for (Node node : jenkinsInstance.getNodes()) {
            if (shouldStopNode(node)) {
                LOGGER.log(Level.FINEST, "{0} should be stopped", node.getNodeName());
                stopNode(node);
            }
        }
    }

    @Override public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    private boolean shouldStopNode(Node node) {
        if (!isStartStopNodes() || node == null) {
            return false;
        }

        Computer computer = getComputer(node);
        if (computer != null && !computer.isConnecting() && computer.isOffline()) {
            boolean foundPendingJobForNode = false;
            Queue.Item[] items = getItems();
            for (Queue.Item item : items) {
                Label itemLabel = item.getAssignedLabel();
                if (itemLabel != null) {
                    if(itemLabel.matches(node)) {
                        foundPendingJobForNode = true;
                        break;
                    }
                }
            }
            return !foundPendingJobForNode;
        }
        return false;
    }

    private void stopNode(Node node) {
        Jenkins jenkinsInstance = Jenkins.get();

        for (Cloud cloud : jenkinsInstance.clouds) {
            if (!(cloud instanceof AmazonEC2Cloud))
                continue;
            AmazonEC2Cloud ec2 = (AmazonEC2Cloud) cloud;
            if (ec2.isStartStopNodes() && ec2.isEc2Node(node)) {
                LOGGER.log(Level.FINE, "Requesting stop on {0} of {1}", new Object[] {ec2.getCloudName(), node.getNodeName()});
                try {
                    ec2.stopNode(node);
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, "Unable to start an EC2 Instance for node: " + node.getNodeName(), e);
                }
            }
        }
    }

    private boolean isStartStopNodes() {
        Jenkins jenkinsInstance = Jenkins.get();
        for (Cloud cloud : jenkinsInstance.clouds) {
            if (!(cloud instanceof AmazonEC2Cloud))
                continue;
            AmazonEC2Cloud ec2 = (AmazonEC2Cloud) cloud;
            return ec2.isStartStopNodes();
        }
        return false;
    }

    @VisibleForTesting
    protected Computer getComputer(Node node) {
        return node.toComputer();
    }

    @VisibleForTesting
    protected Queue.Item[] getItems() {
        Queue queue = Jenkins.get().getQueue();
        if (queue != null) {
            return queue.getItems();
        }
        return new Queue.Item[0];
    }
}
