package hudson.model;

import hudson.Util;
import hudson.util.CaseInsensitiveComparator;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.text.ParseException;

/**
 * Displays {@link Job}s in a flat list view.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListView extends View {
    /**
     * List of job names. This is what gets serialized.
     */
    /*package*/ final Set<String> jobNames = new TreeSet<String>(CaseInsensitiveComparator.INSTANCE);

    /**
     * Include regex string.
     */
    private String includeRegex;
    
    /**
     * Compiled include pattern from the includeRegex string.
     */
    private transient Pattern includePattern;

    @DataBoundConstructor
    public ListView(String name) {
        super(name);
    }

    /**
     * Returns a read-only view of all {@link Job}s in this view.
     *
     * <p>
     * This method returns a separate copy each time to avoid
     * concurrent modification issue.
     */
    public synchronized List<TopLevelItem> getItems() {
        Set<String> names = (Set<String>) ((TreeSet<String>) jobNames).clone();

        if (includeRegex != null) {
            try {
                if (includePattern == null) {
                    includePattern = Pattern.compile(includeRegex);
                }

                for (TopLevelItem item : Hudson.getInstance().getItems()) {
                    String itemName = item.getName();
                    if (includePattern.matcher(itemName).matches()) {
                        names.add(itemName);
                    }
                }
            } catch (PatternSyntaxException pse) {
            }
        }

        List<TopLevelItem> items = new ArrayList<TopLevelItem>(names.size());
        for (String name : names) {
            TopLevelItem item = Hudson.getInstance().getItem(name);
            if(item!=null)
                items.add(item);
        }
        return items;
    }

    public TopLevelItem getItem(String name) {
        return Hudson.getInstance().getItem(name);
    }

    public TopLevelItem getJob(String name) {
        return getItem(name);
    }

    public boolean contains(TopLevelItem item) {
        return jobNames.contains(item.getName());
    }

    public String getIncludeRegex() {
        return includeRegex;
    }

    public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Item item = Hudson.getInstance().doCreateItem(req, rsp);
        if(item!=null) {
            jobNames.add(item.getName());
            owner.save();
        }
        return item;
    }

    @Override
    public synchronized void onJobRenamed(Item item, String oldName, String newName) {
        jobNames.remove(oldName);
        if(newName!=null)
            jobNames.add(newName);
    }

    /**
     * Accepts submission from the configuration page.
     */
    public synchronized void doConfigSubmit( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(CONFIGURE);

        req.setCharacterEncoding("UTF-8");
        
        jobNames.clear();
        for (TopLevelItem item : Hudson.getInstance().getItems()) {
            if(req.getParameter(item.getName())!=null)
                jobNames.add(item.getName());
        }

        description = Util.nullify(req.getParameter("description"));
        
        if (req.getParameter("useincluderegex") != null) {
            includeRegex = Util.nullify(req.getParameter("includeregex"));
        } else {
            includeRegex = null;
        }
        includePattern = null;

        try {
            rename(req.getParameter("name"));
        } catch (ParseException e) {
            sendError(e, req, rsp);
            return;
        }

        owner.save();

        rsp.sendRedirect2("../"+name);
    }

    /**
     * Checks if the include regular expression is valid.
     */
    public void doIncludeRegexCheck( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException, InterruptedException  {
        new FormFieldValidator(req, rsp, false) {
            @Override
            protected void check() throws IOException, ServletException {
                String v = Util.fixEmpty(request.getParameter("value"));
                if (v != null) {
                    try {
                        Pattern.compile(v);
                    } catch (PatternSyntaxException pse) {
                        error(pse.getMessage());
                    }
                }
                ok();
            }
        }.process();
    }

    public ViewDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    static {
        LIST.add(DESCRIPTOR);
    }

    public static final class DescriptorImpl extends ViewDescriptor {
        private DescriptorImpl() {
            super(ListView.class);
        }

        public String getDisplayName() {
            return "List View";
        }
    }
}
