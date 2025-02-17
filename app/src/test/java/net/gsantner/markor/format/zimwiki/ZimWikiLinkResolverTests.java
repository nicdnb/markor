package net.gsantner.markor.format.zimwiki;

import net.gsantner.opoc.util.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZimWikiLinkResolverTests {
    private Path tempFolder;
    private Path notebookRoot;


    @Before
    public void before() {
        try {
            tempFolder = Files.createTempDirectory("markorTemp");
            notebookRoot = Files.createDirectory(tempFolder.resolve("notebookRoot"));
            createTestNotebookStructure();
            System.out.println("Created test notebook in: "+ tempFolder);
        } catch (IOException e) {
            e.printStackTrace();
            fail("Could not create the test directory");
        }
    }

    /**
     * Creates the following notebook structure:
     *
     * notebookRoot ___ My page ___ Another page ___ Very cool subpage
     *   |                   |___ Yet another page ___ Interesting page
     *   |                                      |___ Strange page
     *   |___ Your page ___ Another page
     *   |            |___ The coolest page
     *   |___ Another page
     */
    private void createTestNotebookStructure() throws IOException {

        Files.createDirectories(notebookRoot.resolve("My_page/Another_page"));
        Files.createFile(notebookRoot.resolve("My_page.txt"));
        Files.createFile(notebookRoot.resolve("My_page/Another_page.txt"));
        Files.createFile(notebookRoot.resolve("My_page/Another_page/Very_cool_subpage.txt"));

        Files.createDirectories(notebookRoot.resolve("My_page/Yet_another_page"));
        Files.createFile(notebookRoot.resolve("My_page/Yet_another_page.txt"));
        Files.createFile(notebookRoot.resolve("My_page/Yet_another_page/Interesting_page.txt"));
        Files.createFile(notebookRoot.resolve("My_page/Yet_another_page/Strange_page.txt"));

        Files.createDirectories(notebookRoot.resolve("Your_page"));
        Files.createFile(notebookRoot.resolve("Your_page.txt"));
        Files.createFile(notebookRoot.resolve("Your_page/Another_page.txt"));
        Files.createFile(notebookRoot.resolve("Your_page/The_coolest_page.txt"));

        Files.createFile(notebookRoot.resolve("Another_page.txt"));
    }

    @After
    public void after() {
        FileUtils.deleteRecursive(tempFolder.toFile());
        System.out.println("Deleted: "+tempFolder);
    }

    @Test
    public void resolvesSubPageLinkDirectSubpage() {
        assertResolvedLinkAndDescription("My_page/Another_page.txt", null,
                "[[+Another page]]", "My_page.txt");
    }

    @Test
    public void resolvesSubPageLinkSubpageWithDescription() {
        assertResolvedLinkAndDescription("My_page/Yet_another_page/Strange_page.txt", "This link leads to a strange page",
                "[[+Yet another page:Strange page|This link leads to a strange page]]", "My_page.txt");
    }

    @Test
    public void resolvesTopLevelLink() {
        assertResolvedLinkAndDescription("Your_page/The_coolest_page.txt", null,
                "[[:Your page:The coolest page]]", "My_page/Yet_another_page.txt");
    }

    @Test
    public void resolvesTopLevelLinkWithDescription() {
        assertResolvedLinkAndDescription("My_page/Yet_another_page/Interesting_page.txt", "Some description",
                "[[:My page:Yet another page:Interesting page|Some description]]", "My_page.txt");
    }

    @Test
    public void resolvesRelativeLinkFromParent() {
        assertResolvedLinkAndDescription("My_page/Yet_another_page/Interesting_page.txt", "This is a relative link.",
                "[[Yet another page:Interesting page|This is a relative link.]]", "My_page/Another_page.txt");
    }

    @Test
    public void resolvesRelativeLinkIncludingCurrentPage() {
        assertResolvedLinkAndDescription("My_page/Another_page/Very_cool_subpage.txt", null,
                "[[My page:Another page:Very cool subpage]]", "My_page/Another_page.txt");
    }

    @Test
    public void resolvesRelativeLinkFromRootSibling() {
        assertResolvedLinkAndDescription("Your_page/Another_page.txt", "This link starts at a root page.",
                "[[Your page:Another page|This link starts at a root page.]]", "My_page/Another_page.txt");
    }

    @Test
    public void resolvesRelativeLinkToNearestReachablePage() {
        // make sure that the current page is returned and not the likewise-named root page
        assertResolvedLinkAndDescription("My_page/Another_page.txt", null,
                "[[Another page]]", "My_page/Another_page.txt");
    }

    @Test
    public void returnsNullIfRelativePageCannotBeFound() {
        assertResolvedLinkAndDescription(null, null,
                "[[Non_existing_page.txt]]", "My_page/Another_page.txt");
    }

    @Test
    public void resolvesWebLinkWithDescription() {
        ZimWikiLinkResolver resolver = ZimWikiLinkResolver.resolve("[[http://www.example.com|Example website]]", notebookRoot.toFile(), notebookRoot.resolve("My_page.txt").toFile(), false);
        assertEquals("http://www.example.com", resolver.getResolvedLink());
        assertEquals("Example website", resolver.getLinkDescription());
        assertTrue(resolver.isWebLink());
    }

    @Test
    public void resolvesTopLevelLinkWithDynamicallyDeterminedRoot() throws IOException {
        Files.createFile(notebookRoot.resolve("notebook.zim"));
        ZimWikiLinkResolver resolver = ZimWikiLinkResolver.resolve("[[:Your page:The coolest page]]", null, notebookRoot.resolve("My_page/Yet_another_page.txt").toFile(), true);
        assertEquals(notebookRoot.toFile(), resolver.getNotebookRootDir());
        String expectedLink = notebookRoot.resolve("Your_page/The_coolest_page.txt").toString();
        assertEquals(expectedLink, resolver.getResolvedLink());
    }

    @Test
    public void doesNotResolveTopLevelLinkIfRootCannotBeDetermined() {
        ZimWikiLinkResolver resolver = ZimWikiLinkResolver.resolve("[[:Your page:The coolest page]]", null, notebookRoot.resolve("My_page/Yet_another_page.txt").toFile(), true);
        assertNull(resolver.getNotebookRootDir());
        assertNull(resolver.getResolvedLink());
    }

    private void assertResolvedLinkAndDescription(String expectedLinkRelativeToRoot, String expectedDescription, String zimLink, String currentPageRelativeToRoot) {
        ZimWikiLinkResolver resolver = ZimWikiLinkResolver.resolve(zimLink, notebookRoot.toFile(), notebookRoot.resolve(currentPageRelativeToRoot).toFile(), false);
        String expectedLink = expectedLinkRelativeToRoot!=null ? notebookRoot.resolve(expectedLinkRelativeToRoot).toString() : null;
        assertEquals(expectedLink, resolver.getResolvedLink());
        assertEquals(expectedDescription, resolver.getLinkDescription());
        assertFalse(resolver.isWebLink());
    }
}
