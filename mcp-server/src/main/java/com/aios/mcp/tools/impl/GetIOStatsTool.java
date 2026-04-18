package com.aios.mcp.tools.impl;

import com.aios.mcp.tools.McpTool;
import com.aios.mcp.tools.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.List;

/**
 * MCP Tool for retrieving I/O statistics.
 * Provides disk I/O metrics, file system information, and storage capacity.
 */
@Component
public class GetIOStatsTool implements McpTool {

    private final ObjectMapper objectMapper;
    private final SystemInfo systemInfo;

    public GetIOStatsTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.systemInfo = new SystemInfo();
    }

    @Override
    public String getName() {
        return "get_io_stats";
    }

    @Override
    public String getDescription() {
        return "Retrieves I/O statistics including disk read/write speeds, " +
                "file system usage, and storage capacity information.";
    }

    @Override
    public JsonNode execute(JsonNode parameters) throws McpToolException {
        try {
            boolean includePartitions = parameters.has("includePartitions") &&
                    parameters.get("includePartitions").asBoolean(true);
            boolean includeFileSystems = parameters.has("includeFileSystems") &&
                    parameters.get("includeFileSystems").asBoolean(true);

            HardwareAbstractionLayer hardware = systemInfo.getHardware();
            OperatingSystem os = systemInfo.getOperatingSystem();

            ObjectNode result = objectMapper.createObjectNode();

            // Disk stores information
            ArrayNode diskArray = objectMapper.createArrayNode();
            List<HWDiskStore> diskStores = hardware.getDiskStores();

            long totalReads = 0;
            long totalWrites = 0;
            long totalReadBytes = 0;
            long totalWriteBytes = 0;

            for (HWDiskStore disk : diskStores) {
                ObjectNode diskNode = objectMapper.createObjectNode();
                diskNode.put("name", disk.getName());
                diskNode.put("model", disk.getModel());
                diskNode.put("serial", disk.getSerial());
                diskNode.put("size", disk.getSize());
                diskNode.put("sizeGB", disk.getSize() / (1024L * 1024L * 1024L));
                diskNode.put("reads", disk.getReads());
                diskNode.put("writes", disk.getWrites());
                diskNode.put("readBytes", disk.getReadBytes());
                diskNode.put("writeBytes", disk.getWriteBytes());
                diskNode.put("readBytesMB", disk.getReadBytes() / (1024L * 1024L));
                diskNode.put("writeBytesMB", disk.getWriteBytes() / (1024L * 1024L));
                diskNode.put("currentQueueLength", disk.getCurrentQueueLength());
                diskNode.put("transferTime", disk.getTransferTime());

                totalReads += disk.getReads();
                totalWrites += disk.getWrites();
                totalReadBytes += disk.getReadBytes();
                totalWriteBytes += disk.getWriteBytes();

                if (includePartitions) {
                    ArrayNode partitions = objectMapper.createArrayNode();
                    disk.getPartitions().forEach(partition -> {
                        ObjectNode partNode = objectMapper.createObjectNode();
                        partNode.put("name", partition.getName());
                        partNode.put("identifier", partition.getIdentification());
                        partNode.put("type", partition.getType());
                        partNode.put("mountPoint", partition.getMountPoint());
                        partNode.put("size", partition.getSize());
                        partNode.put("sizeGB", partition.getSize() / (1024L * 1024L * 1024L));
                        partitions.add(partNode);
                    });
                    diskNode.set("partitions", partitions);
                }

                diskArray.add(diskNode);
            }

            result.put("diskCount", diskStores.size());
            result.set("disks", diskArray);

            // Summary metrics
            ObjectNode summary = objectMapper.createObjectNode();
            summary.put("totalReads", totalReads);
            summary.put("totalWrites", totalWrites);
            summary.put("totalReadBytesMB", totalReadBytes / (1024L * 1024L));
            summary.put("totalWriteBytesMB", totalWriteBytes / (1024L * 1024L));
            result.set("summary", summary);

            // File systems information
            if (includeFileSystems) {
                FileSystem fileSystem = os.getFileSystem();
                ArrayNode fsArray = objectMapper.createArrayNode();

                for (OSFileStore fs : fileSystem.getFileStores()) {
                    ObjectNode fsNode = objectMapper.createObjectNode();
                    fsNode.put("name", fs.getName());
                    fsNode.put("label", fs.getLabel());
                    fsNode.put("mount", fs.getMount());
                    fsNode.put("type", fs.getType());
                    fsNode.put("totalSpace", fs.getTotalSpace());
                    fsNode.put("totalSpaceGB", fs.getTotalSpace() / (1024L * 1024L * 1024L));
                    fsNode.put("usableSpace", fs.getUsableSpace());
                    fsNode.put("usableSpaceGB", fs.getUsableSpace() / (1024L * 1024L * 1024L));
                    fsNode.put("freeSpace", fs.getFreeSpace());
                    fsNode.put("freeSpaceGB", fs.getFreeSpace() / (1024L * 1024L * 1024L));

                    long usedSpace = fs.getTotalSpace() - fs.getFreeSpace();
                    fsNode.put("usedSpace", usedSpace);
                    fsNode.put("usedSpaceGB", usedSpace / (1024L * 1024L * 1024L));

                    double usagePercent = fs.getTotalSpace() > 0
                            ? (usedSpace * 100.0) / fs.getTotalSpace()
                            : 0;
                    fsNode.put("usagePercent", Math.round(usagePercent * 100.0) / 100.0);

                    fsNode.put("description", fs.getDescription());
                    fsArray.add(fsNode);
                }

                result.put("fileSystemCount", fsArray.size());
                result.set("fileSystems", fsArray);
            }

            return result;

        } catch (Exception e) {
            throw new McpToolException(getName(),
                    "Failed to get I/O statistics: " + e.getMessage(), e);
        }
    }

    @Override
    public JsonNode getSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode includePartitions = objectMapper.createObjectNode();
        includePartitions.put("type", "boolean");
        includePartitions.put("description", "Include partition information for each disk");
        includePartitions.put("default", true);
        properties.set("includePartitions", includePartitions);

        ObjectNode includeFileSystems = objectMapper.createObjectNode();
        includeFileSystems.put("type", "boolean");
        includeFileSystems.put("description", "Include file system usage information");
        includeFileSystems.put("default", true);
        properties.set("includeFileSystems", includeFileSystems);

        schema.set("properties", properties);
        return schema;
    }

    @Override
    public String getCategory() {
        return "system";
    }

    @Override
    public SafetyLevel getSafetyLevel() {
        return SafetyLevel.LOW;
    }
}
