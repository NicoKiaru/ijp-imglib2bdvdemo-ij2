package ch.epfl.biop.docs;

import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.command.DynamicCommandInfo;
import org.scijava.log.LogService;
import org.scijava.module.Module;
import org.scijava.module.process.AbstractPostprocessorPlugin;
import org.scijava.module.process.PostprocessorPlugin;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.search.SourceFinder;
import org.scijava.search.SourceNotFoundException;
import org.scijava.util.VersionUtils;

import ch.epfl.biop.docs.videos.core.CommandRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(type = PostprocessorPlugin.class)
public class CatchCommandForDocPostProcessor extends AbstractPostprocessorPlugin {

    final public static Map<String, CommandDoc> usedCommands = new HashMap<>();

    @Parameter
    Context ctx;

    @Override
    public void process(Module module) {
        if (module.getInfo() instanceof CommandInfo) {
            CommandInfo ci = (CommandInfo) module.getInfo();
            if (!usedCommands.containsKey(ci.getPluginClass().getName())) {
                CommandDoc cd = new CommandDoc();
                cd.version = VersionUtils.getVersion(ci.getPluginClass());
                try {
                    cd.codeUrl = SourceFinder.sourceLocation(ci.getPluginClass(), ctx.getService(LogService.class)).toString();
                } catch (SourceNotFoundException e) {
                    throw new RuntimeException(e);
                }
                cd.name = ci.getName();
                usedCommands.put(ci.getPluginClass().getName(), cd);
            }

        } else if (module.getInfo() instanceof DynamicCommandInfo) {
            DynamicCommandInfo ci = (DynamicCommandInfo) module.getInfo();
            if (!usedCommands.containsKey(ci.getDelegateClassName())) {
                CommandDoc cd = new CommandDoc();
                try {
                    cd.version = VersionUtils.getVersion(Class.forName(ci.getDelegateClassName()));
                    cd.codeUrl = SourceFinder.sourceLocation(Class.forName(ci.getDelegateClassName()), ctx.getService(LogService.class)).toString();
                } catch (SourceNotFoundException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                cd.name = ci.getName();
                usedCommands.put(ci.getDelegateClassName(), cd);
            }
        }
    }

    /**
     * Snapshot every command invoked so far as neutral {@link CommandRef}
     * DTOs, sorted by display name. Used by {@code videos/Demo} to populate
     * the {@code outro.commands[]} block of {@code timeline.json} (and the
     * generated {@code youtube-description.md}) without exposing the
     * package-private {@link CommandDoc} type outside this package.
     */
    public static List<CommandRef> usedCommandRefs() {
        List<CommandRef> refs = new ArrayList<>();
        for (CommandDoc cd : usedCommands.values()) {
            refs.add(new CommandRef(cd.name, cd.version, cd.codeUrl));
        }
        refs.sort(Comparator.comparing(c -> c.name == null ? "" : c.name));
        return refs;
    }

    static class CommandDoc {
        String name;
        String version;
        String codeUrl;
    }
}
