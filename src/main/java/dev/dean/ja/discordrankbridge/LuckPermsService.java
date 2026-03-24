package dev.dean.ja.discordrankbridge;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service to interact with LuckPerms API.
 */
public class LuckPermsService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuckPermsService.class);
    private final LuckPerms luckPerms;

    public LuckPermsService() {
        this.luckPerms = LuckPermsProvider.get();
    }

    /**
     * Updates a player's group based on priority.
     * Only the highest priority group is kept.
     */
    public CompletableFuture<Boolean> updatePlayerGroup(String minecraftName, String groupName, String action) {
        if (minecraftName == null || minecraftName.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return luckPerms.getUserManager().lookupUniqueId(minecraftName).thenCompose(uuid -> {
            if (uuid == null) {
                LOGGER.error("Could not find UUID for player: {}", minecraftName);
                return CompletableFuture.completedFuture(false);
            }
            return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
                if (user == null) {
                    LOGGER.error("Could not load LuckPerms user for UUID: {}", uuid);
                    return false;
                }

                boolean changed = false;
                if ("add".equalsIgnoreCase(action)) {
                    changed = handleAdd(user, groupName);
                } else if ("remove".equalsIgnoreCase(action)) {
                    changed = handleRemove(user, groupName);
                }

                if (changed) {
                    luckPerms.getUserManager().saveUser(user);
                }
                return true;
            });
        }).exceptionally(throwable -> {
            LOGGER.error("Error updating LuckPerms group for player: {}", minecraftName, throwable);
            return false;
        });
    }

    /**
     * Fetches the highest priority managed group for a player.
     */
    public CompletableFuture<String> getHighestPriorityGroup(String minecraftName) {
        if (minecraftName == null || minecraftName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return luckPerms.getUserManager().lookupUniqueId(minecraftName).thenCompose(uuid -> {
            if (uuid == null) return CompletableFuture.completedFuture(null);
            return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> {
                if (user == null) return null;

                Collection<InheritanceNode> currentNodes = user.getNodes(NodeType.INHERITANCE);
                String highestGroup = null;
                int highestPriority = Integer.MAX_VALUE;

                for (InheritanceNode node : currentNodes) {
                    String name = node.getGroupName();
                    if (Config.COMMON.isManagedGroup(name)) {
                        int priority = Config.COMMON.getGroupPriority(name);
                        if (priority < highestPriority) {
                            highestPriority = priority;
                            highestGroup = name;
                        }
                    }
                }
                return highestGroup;
            });
        }).exceptionally(throwable -> {
            LOGGER.error("Error fetching highest priority group for player: {}", minecraftName, throwable);
            return null;
        });
    }

    private boolean handleAdd(User user, String newGroup) {
        int newPriority = Config.COMMON.getGroupPriority(newGroup);
        
        // Find current managed groups
        Collection<InheritanceNode> currentNodes = user.getNodes(NodeType.INHERITANCE);
        InheritanceNode highestCurrent = null;
        int highestPriority = Integer.MAX_VALUE;

        for (InheritanceNode node : currentNodes) {
            String name = node.getGroupName();
            if (Config.COMMON.isManagedGroup(name)) {
                int priority = Config.COMMON.getGroupPriority(name);
                if (priority < highestPriority) {
                    highestPriority = priority;
                    highestCurrent = node;
                }
            }
        }

        if (newPriority < highestPriority) {
            // New group is higher priority (lower number)
            // Remove all other managed groups
            user.data().clear(NodeType.INHERITANCE.predicate(node -> Config.COMMON.isManagedGroup(node.getGroupName())));
            user.data().add(InheritanceNode.builder(newGroup).build());
            LOGGER.info("Applied higher priority group {} to user {}", newGroup, user.getUsername());
            return true;
        } else {
            // New group is lower or equal priority.
            // If we don't have any managed group yet, add it.
            if (highestCurrent == null) {
                user.data().add(InheritanceNode.builder(newGroup).build());
                LOGGER.info("Applied initial group {} to user {}", newGroup, user.getUsername());
                return true;
            }
            LOGGER.info("Skipped applying group {} to user {} because current group {} has higher or equal priority", 
                    newGroup, user.getUsername(), highestCurrent.getGroupName());
            return false;
        }
    }

    private boolean handleRemove(User user, String groupToRemove) {
        // Just remove the specific group
        user.data().remove(InheritanceNode.builder(groupToRemove).build());
        LOGGER.info("Removed group {} from user {}", groupToRemove, user.getUsername());
        return true;
    }

    /**
     * Checks if a player has at least the specified minimum rank based on priority.
     * Lower priority number = higher rank.
     * 
     * @param minecraftName The player's Minecraft username
     * @param minimumRank The minimum required rank (e.g., "trialmod")
     * @return CompletableFuture<Boolean> true if player has this rank or higher
     */
    public CompletableFuture<Boolean> hasMinimumRank(String minecraftName, String minimumRank) {
        if (minecraftName == null || minimumRank == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        int minimumPriority = Config.COMMON.getGroupPriority(minimumRank);
        if (minimumPriority == Integer.MAX_VALUE) {
            // Minimum rank not found in config
            LOGGER.warn("Minimum rank '{}' not found in config", minimumRank);
            return CompletableFuture.completedFuture(false);
        }
        
        return getHighestPriorityGroup(minecraftName).thenApply(playerGroup -> {
            if (playerGroup == null) {
                LOGGER.debug("Player {} has no managed group", minecraftName);
                return false;
            }
            
            int playerPriority = Config.COMMON.getGroupPriority(playerGroup);
            // Lower priority number = higher rank
            boolean hasMinRank = playerPriority <= minimumPriority;
            LOGGER.debug("Player {} has group {} (priority {}) - minimum rank {} (priority {}) - allowed: {}", 
                    minecraftName, playerGroup, playerPriority, minimumRank, minimumPriority, hasMinRank);
            return hasMinRank;
        });
    }
}
