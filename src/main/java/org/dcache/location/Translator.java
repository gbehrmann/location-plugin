package org.dcache.location;

import com.google.common.base.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import dmg.cells.nucleus.EnvironmentAware;

public class Translator implements EnvironmentAware
{
    private static final String PREFIX = "location.pattern.";
    private static final Logger LOGGER = LoggerFactory.getLogger(Translator.class);
    private final LinkedHashMap<Pattern,String> patterns = new LinkedHashMap<>();

    @Override
    public void setEnvironment(Map<String, Object> env)
    {
        for (int i = 0; env.containsKey(PREFIX + i); i++) {
            String s = env.get(PREFIX + i).toString().trim();
            if (!s.isEmpty()) {
                char delim = s.charAt(0);
                int endPattern = s.indexOf(delim, 1);
                if (endPattern == -1) {
                    LOGGER.error("Invalid location service pattern: " + s);
                }
                int endReplacement = s.indexOf(delim, endPattern + 1);
                if (endReplacement == -1) {
                    LOGGER.error("Invalid location service pattern: " + s);
                }

                String pattern = s.substring(1, endPattern);
                String replacement = s.substring(endPattern + 1, endReplacement);

                patterns.put(Pattern.compile(pattern), replacement);
            }
        }
    }

    public String translate(String s)
    {
        for (Map.Entry<Pattern, String> entry : patterns.entrySet()) {
            s = entry.getKey().matcher(s).replaceFirst(entry.getValue());
        }
        return s;
    }

    public Function<String, String> translateFunction()
    {
        return new Function<String, String>()
        {
            @Override
            public String apply(String s)
            {
                return translate(s);
            }
        };
    }
}
