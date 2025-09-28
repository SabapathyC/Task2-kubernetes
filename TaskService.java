import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

public TaskExecution executeTaskInPod(String taskId) throws Exception {
    Task task = repository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not found"));

    TaskExecution execution = new TaskExecution();
    execution.setStartTime(new Date());

    // Initialize Kubernetes client
    ApiClient client = Config.defaultClient();
    CoreV1Api api = new CoreV1Api(client);

    // Define pod
    V1Pod pod = new V1PodBuilder()
        .withNewMetadata()
            .withGenerateName("task-exec-")
        .endMetadata()
        .withNewSpec()
            .addNewContainer()
                .withName("busybox")
                .withImage("busybox")
                .withCommand("sh", "-c", task.getCommand())
            .endContainer()
            .withRestartPolicy("Never")
        .endSpec()
        .build();

    // Create pod
    V1Pod createdPod = api.createNamespacedPod("default", pod, null, null, null);

    // Wait for pod to complete
    String podName = createdPod.getMetadata().getName();
    boolean completed = false;
    while (!completed) {
        V1Pod podStatus = api.readNamespacedPod(podName, "default", null);
        String phase = podStatus.getStatus().getPhase();
        if ("Succeeded".equals(phase) || "Failed".equals(phase)) completed = true;
        Thread.sleep(1000);
    }

    // Get pod logs (command output)
    String logs = api.readNamespacedPodLog(podName, "default", null, null, null, null, null, null, null, null);
    execution.setOutput(logs);
    execution.setEndTime(new Date());

    // Save execution
    task.getTaskExecutions().add(execution);
    repository.save(task);

    // Delete pod
    api.deleteNamespacedPod(podName, "default", null, null, null, null, null, null);

    return execution;
}
