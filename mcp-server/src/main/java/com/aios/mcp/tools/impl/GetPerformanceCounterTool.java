package com.aios.mcp.tools.impl;

import com.aios.mcp.tools.McpTool;
import com.aios.mcp.tools.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP Tool for retrieving performance counter data.
 * Provides CPU, memory, disk, and network performance metrics.
 */
@Component
public class GetPerformanceCounterTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    // Available counter categories
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "cpu", "memory", "disk", "network", "system", "all");

    // Store previous CPU load ticks for delta calculation
    private long[] prevTicks;
    private long[][] prevProcTicks;

    public GetPerformanceCounterTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "get_performance_counters";
    }

    @Override
    public String getDescription() {
        return "Retrieves Windows performance counter data including CPU usage, " +
                "memory statistics, disk I/O, and network throughput metrics.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        String category = parameters.has("category")
                ? parameters.get("category").asText("all")
                : "all";
        boolean detailed = parameters.has("detailed")
                && parameters.get("detailed").asBoolean(false);

        // Validate category
        if (!VALID_CATEGORIES.contains(category.toLowerCase())) {
            throw new McpToolException(getName(),
                    "Invalid category: " + category + ". Valid options: " + VALID_CATEGORIES,
                    McpToolException.ERROR_INVALID_PARAMETERS);
        }

        try {
            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();

            ObjectNode result = objectMapper.createObjectNode();
            result.put("timestamp", System.currentTimeMillis());
            result.put("category", category);

            boolean all = "all".equalsIgnoreCase(category);

            if (all || "cpu".equalsIgnoreCase(category)) {
                result.set("cpu", getCpuCounters(hardware.getProcessor(), detailed));
            }

            if (all || "memory".equalsIgnoreCase(category)) {
                result.set("memory", getMemoryCounters(hardware.getMemory(), detailed));
            }

            if (all || "disk".equalsIgnoreCase(category)) {
                result.set("disk", getDiskCounters(hardware, detailed));
            }

            if (all || "network".equalsIgnoreCase(category)) {
                result.set("network", getNetworkCounters(hardware.getNetworkIFs(), detailed));
            }

            if (all || "system".equalsIgnoreCase(category)) {
                result.set("system", getSystemCounters(os, hardware, detailed));
            }

            return result;

        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to get performance counters: " + e.getMessage(), e);
        }
    }

    private ObjectNode getCpuCounters(CentralProcessor processor, boolean detailed) {
        ObjectNode cpu = objectMapper.createObjectNode();

        // Get current CPU load
        long[] currentTicks = processor.getSystemCpuLoadTicks();
        double cpuLoad;

        if (prevTicks != null) {
            cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        } else {
            cpuLoad = processor.getSystemCpuLoad(1000) * 100;
        }
        prevTicks = currentTicks;

        cpu.put("loadPercent", Math.round(cpuLoad * 100.0) / 100.0);
        cpu.put("physicalCores", processor.getPhysicalProcessorCount());
        cpu.put("logicalCores", processor.getLogicalProcessorCount());

        // CPU frequency
        long maxFreq = processor.getMaxFreq();
        cpu.put("maxFrequencyMHz", maxFreq / 1_000_000);

        long[] currentFreqs = processor.getCurrentFreq();
        if (currentFreqs.length > 0) {
            long avgFreq = 0;
            for (long freq : currentFreqs) {
                avgFreq += freq;
            }
            avgFreq /= currentFreqs.length;
            cpu.put("currentFrequencyMHz", avgFreq / 1_000_000);
        }

        // Context switches and interrupts
        cpu.put("contextSwitches", processor.getContextSwitches());
        cpu.put("interrupts", processor.getInterrupts());

        if (detailed) {
            // Per-core load
            double[] perCoreLoad = processor.getProcessorCpuLoad(500);
            ArrayNode coresArray = objectMapper.createArrayNode();
            for (int i = 0; i < perCoreLoad.length; i++) {
                ObjectNode coreNode = objectMapper.createObjectNode();
                coreNode.put("core", i);
                coreNode.put("loadPercent", Math.round(perCoreLoad[i] * 10000.0) / 100.0);
                if (i < currentFreqs.length) {
                    coreNode.put("frequencyMHz", currentFreqs[i] / 1_000_000);
                }
                coresArray.add(coreNode);
            }
            cpu.set("cores", coresArray);

            // Tick breakdown
            ObjectNode ticks = objectMapper.createObjectNode();
            CentralProcessor.TickType[] tickTypes = CentralProcessor.TickType.values();
            for (int i = 0; i < tickTypes.length && i < currentTicks.length; i++) {
                ticks.put(tickTypes[i].name().toLowerCase(), currentTicks[i]);
            }
            cpu.set("ticks", ticks);
        }

        return cpu;
    }

    private ObjectNode getMemoryCounters(GlobalMemory memory, boolean detailed) {
        ObjectNode mem = objectMapper.createObjectNode();

        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;

        mem.put("totalBytes", total);
        mem.put("totalGB", Math.round(total / (1024.0 * 1024 * 1024) * 100.0) / 100.0);
        mem.put("availableBytes", available);
        mem.put("availableGB", Math.round(available / (1024.0 * 1024 * 1024) * 100.0) / 100.0);
        mem.put("usedBytes", used);
        mem.put("usedGB", Math.round(used / (1024.0 * 1024 * 1024) * 100.0) / 100.0);
        mem.put("usedPercent", Math.round((used * 10000.0) / total) / 100.0);

        // Page file / Virtual memory
        mem.put("pageSize", memory.getPageSize());

        if (detailed) {
            // Virtual memory info
            ObjectNode virtualMem = objectMapper.createObjectNode();
            var virtualMemory = memory.getVirtualMemory();
            virtualMem.put("swapTotal", virtualMemory.getSwapTotal());
            virtualMem.put("swapUsed", virtualMemory.getSwapUsed());
            virtualMem.put("swapPagesIn", virtualMemory.getSwapPagesIn());
            virtualMem.put("swapPagesOut", virtualMemory.getSwapPagesOut());
            virtualMem.put("virtualMax", virtualMemory.getVirtualMax());
            virtualMem.put("virtualInUse", virtualMemory.getVirtualInUse());
            mem.set("virtualMemory", virtualMem);

            // Physical memory info
            ArrayNode physicalArray = objectMapper.createArrayNode();
            memory.getPhysicalMemory().forEach(pm -> {
                ObjectNode pmNode = objectMapper.createObjectNode();
                pmNode.put("bankLabel", pm.getBankLabel());
                pmNode.put("manufacturer", pm.getManufacturer());
                pmNode.put("memoryType", pm.getMemoryType());
                pmNode.put("capacityGB", pm.getCapacity() / (1024L * 1024 * 1024));
                pmNode.put("clockSpeedMHz", pm.getClockSpeed() / 1_000_000);
                physicalArray.add(pmNode);
            });
            mem.set("physicalMemory", physicalArray);
        }

        return mem;
    }

    private ObjectNode getDiskCounters(HardwareAbstractionLayer hardware, boolean detailed) {
        ObjectNode disk = objectMapper.createObjectNode();

        var diskStores = hardware.getDiskStores();

        long totalReads = 0;
        long totalWrites = 0;
        long totalReadBytes = 0;
        long totalWriteBytes = 0;
        long totalSize = 0;

        ArrayNode disksArray = objectMapper.createArrayNode();

        for (var store : diskStores) {
            totalReads += store.getReads();
            totalWrites += store.getWrites();
            totalReadBytes += store.getReadBytes();
            totalWriteBytes += store.getWriteBytes();
            totalSize += store.getSize();

            if (detailed) {
                ObjectNode diskNode = objectMapper.createObjectNode();
                diskNode.put("name", store.getName());
                diskNode.put("model", store.getModel());
                diskNode.put("sizeGB", store.getSize() / (1024L * 1024 * 1024));
                diskNode.put("reads", store.getReads());
                diskNode.put("writes", store.getWrites());
                diskNode.put("readBytesMB", store.getReadBytes() / (1024 * 1024));
                diskNode.put("writeBytesMB", store.getWriteBytes() / (1024 * 1024));
                diskNode.put("queueLength", store.getCurrentQueueLength());
                diskNode.put("transferTimeMs", store.getTransferTime());
                disksArray.add(diskNode);
            }
        }

        disk.put("totalReads", totalReads);
        disk.put("totalWrites", totalWrites);
        disk.put("totalReadBytesMB", totalReadBytes / (1024 * 1024));
        disk.put("totalWriteBytesMB", totalWriteBytes / (1024 * 1024));
        disk.put("totalSizeGB", totalSize / (1024L * 1024 * 1024));
        disk.put("diskCount", diskStores.size());

        if (detailed && !disksArray.isEmpty()) {
            disk.set("disks", disksArray);
        }

        return disk;
    }

    private ObjectNode getNetworkCounters(List<NetworkIF> networkIFs, boolean detailed) {
        ObjectNode network = objectMapper.createObjectNode();

        long totalBytesRecv = 0;
        long totalBytesSent = 0;
        long totalPacketsRecv = 0;
        long totalPacketsSent = 0;
        long totalErrors = 0;

        ArrayNode interfacesArray = objectMapper.createArrayNode();

        for (NetworkIF net : networkIFs) {
            net.updateAttributes();

            totalBytesRecv += net.getBytesRecv();
            totalBytesSent += net.getBytesSent();
            totalPacketsRecv += net.getPacketsRecv();
            totalPacketsSent += net.getPacketsSent();
            totalErrors += net.getInErrors() + net.getOutErrors();

            if (detailed) {
                ObjectNode netNode = objectMapper.createObjectNode();
                netNode.put("name", net.getName());
                netNode.put("displayName", net.getDisplayName());
                netNode.put("macAddress", net.getMacaddr());
                netNode.put("speedMbps", net.getSpeed() / 1_000_000);
                netNode.put("bytesRecv", net.getBytesRecv());
                netNode.put("bytesSent", net.getBytesSent());
                netNode.put("packetsRecv", net.getPacketsRecv());
                netNode.put("packetsSent", net.getPacketsSent());
                netNode.put("inErrors", net.getInErrors());
                netNode.put("outErrors", net.getOutErrors());
                netNode.put("mtu", net.getMTU());

                ArrayNode ipv4Array = objectMapper.createArrayNode();
                for (String ip : net.getIPv4addr()) {
                    ipv4Array.add(ip);
                }
                netNode.set("ipv4Addresses", ipv4Array);

                ArrayNode ipv6Array = objectMapper.createArrayNode();
                for (String ip : net.getIPv6addr()) {
                    ipv6Array.add(ip);
                }
                netNode.set("ipv6Addresses", ipv6Array);

                interfacesArray.add(netNode);
            }
        }

        network.put("totalBytesRecvMB", totalBytesRecv / (1024 * 1024));
        network.put("totalBytesSentMB", totalBytesSent / (1024 * 1024));
        network.put("totalPacketsRecv", totalPacketsRecv);
        network.put("totalPacketsSent", totalPacketsSent);
        network.put("totalErrors", totalErrors);
        network.put("interfaceCount", networkIFs.size());

        if (detailed && !interfacesArray.isEmpty()) {
            network.set("interfaces", interfacesArray);
        }

        return network;
    }

    private ObjectNode getSystemCounters(OperatingSystem os, HardwareAbstractionLayer hardware,
            boolean detailed) {
        ObjectNode system = objectMapper.createObjectNode();

        system.put("processCount", os.getProcessCount());
        system.put("threadCount", os.getThreadCount());
        system.put("uptime", os.getSystemUptime());
        system.put("uptimeFormatted", formatUptime(os.getSystemUptime()));

        // System boot time
        system.put("bootTime", os.getSystemBootTime());

        if (detailed) {
            // OS info
            ObjectNode osInfo = objectMapper.createObjectNode();
            osInfo.put("manufacturer", os.getManufacturer());
            osInfo.put("family", os.getFamily());
            osInfo.put("version", os.getVersionInfo().getVersion());
            osInfo.put("buildNumber", os.getVersionInfo().getBuildNumber());
            osInfo.put("codeName", os.getVersionInfo().getCodeName());
            system.set("osInfo", osInfo);

            // Computer system info
            var computerSystem = hardware.getComputerSystem();
            ObjectNode computerInfo = objectMapper.createObjectNode();
            computerInfo.put("manufacturer", computerSystem.getManufacturer());
            computerInfo.put("model", computerSystem.getModel());
            computerInfo.put("serialNumber", computerSystem.getSerialNumber());
            system.set("computerSystem", computerInfo);

            // Sensors
            var sensors = hardware.getSensors();
            ObjectNode sensorsNode = objectMapper.createObjectNode();
            sensorsNode.put("cpuTemperature", sensors.getCpuTemperature());
            sensorsNode.put("cpuVoltage", sensors.getCpuVoltage());
            ArrayNode fanSpeeds = objectMapper.createArrayNode();
            for (int speed : sensors.getFanSpeeds()) {
                fanSpeeds.add(speed);
            }
            sensorsNode.set("fanSpeeds", fanSpeeds);
            system.set("sensors", sensorsNode);
        }

        return system;
    }

    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, secs);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode category = objectMapper.createObjectNode();
        category.put("type", "string");
        category.put("description", "Performance counter category to retrieve");
        category.put("default", "all");
        ArrayNode enumValues = objectMapper.createArrayNode();
        enumValues.add("all");
        enumValues.add("cpu");
        enumValues.add("memory");
        enumValues.add("disk");
        enumValues.add("network");
        enumValues.add("system");
        category.set("enum", enumValues);
        properties.set("category", category);

        ObjectNode detailed = objectMapper.createObjectNode();
        detailed.put("type", "boolean");
        detailed.put("description", "Include detailed per-component metrics");
        detailed.put("default", false);
        properties.set("detailed", detailed);

        schema.set("properties", properties);
        return schema;
    }

    @Override
    public String getCategory() {
        return "performance";
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }
}
