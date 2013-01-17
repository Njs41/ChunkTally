package no.runsafe.chunktally;

import no.runsafe.framework.configuration.IConfiguration;
import no.runsafe.framework.event.IConfigurationChanged;
import no.runsafe.framework.event.world.IChunkLoad;
import no.runsafe.framework.output.IOutput;
import no.runsafe.framework.server.RunsafeLocation;
import no.runsafe.framework.server.chunk.RunsafeChunk;
import no.runsafe.framework.server.entity.RunsafeEntity;

import java.util.*;
import java.util.logging.Level;

public class Accountant implements IChunkLoad, IConfigurationChanged
{
	public Accountant(IOutput output)
	{
		this.console = output;
	}

	@Override
	public void OnChunkLoad(RunsafeChunk chunk)
	{
		if (chunk.getEntities().size() > auditLevel)
		{
			if (!auditedWorlds.contains(chunk.getWorld().getName()))
				return;
			AuditEntitiesAboveLimit(chunk);
		}
	}

	@Override
	public void OnConfigurationChanged(IConfiguration config)
	{
		limits.clear();
		limits.putAll(config.getConfigValuesAsIntegerMap("audit.entity.limit"));
		auditedWorlds.clear();
		auditedWorlds.addAll(config.getConfigValueAsList("audit.entity.worlds"));
		auditLevel = config.getConfigValueAsInt("audit.entity.inspect");
	}

	private void AuditEntitiesAboveLimit(RunsafeChunk chunk)
	{
		List<RunsafeEntity> entities = chunk.getEntities();
		if (entities == null)
			return;
		HashMap<String, Integer> counts = new HashMap<String, Integer>();
		HashMap<String, RunsafeLocation> locations = new HashMap<String, RunsafeLocation>();
		for (RunsafeEntity entity : entities)
		{
			String name = entity.getRaw().getType().name();
			if (!locations.containsKey(name))
				locations.put(name, entity.getLocation());
			if (!counts.containsKey(name))
				counts.put(name, 0);
			else
				counts.put(name, counts.get(name) + 1);
		}
		for (String type : counts.keySet())
		{
			if (limits.containsKey(type))
			{
				if (counts.get(type) < limits.get(type))
					counts.remove(type);
			}
			else if (limits.containsKey("default") && counts.get(type) < limits.get("default"))
				counts.remove(type);
		}
		int finalCount = 0;
		for (String type : counts.keySet())
			finalCount += counts.get(type);
		if (finalCount < auditLevel)
			return;

		ArrayList<Map.Entry<String, Integer>> as = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
		Collections.sort(as, new Comparator<Map.Entry<String, Integer>>()
		{
			public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2)
			{
				return o2.getValue().compareTo(o1.getValue());
			}
		});
		Map.Entry<String, Integer> max = as.get(0);
		StringBuilder stats = new StringBuilder();
		for (String type : counts.keySet())
			stats.append(
				String.format(
					"  %d x %s (%d,%d,%d)",
					counts.get(type), type,
					locations.get(type).getBlockX(),
					locations.get(type).getBlockY(),
					locations.get(type).getBlockZ()
				)
			);
		console.writeColoured(
			"&cChunk [%s,%d,%d] is above entity limit! %d > %d&r\n%s",
			Level.WARNING,
			chunk.getWorld().getName(),
			chunk.getX(),
			chunk.getZ(),
			entities.size(),
			auditLevel,
			max.getValue(),
			max.getKey()
		);
	}

	private final IOutput console;
	private final ArrayList<String> auditedWorlds = new ArrayList<String>();
	private final HashMap<String, Integer> limits = new HashMap<String, Integer>();
	private int auditLevel;
}
