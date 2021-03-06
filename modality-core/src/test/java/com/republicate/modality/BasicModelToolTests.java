package com.republicate.modality;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.republicate.modality.config.ConfigurationException;
import com.republicate.modality.filter.Filter;
import com.republicate.modality.filter.ValueFilters;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.Serializable;
import java.sql.Date;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static org.junit.Assert.*;

/**
 * <p>Basic model tests</p>
 *
 * Note: for speed reasons, db state is not resetted between tests ; so each test MUST leave a clean state when succesful.
 *
 * @author Claude Brisson
 * @since VelocityTools 3.1
 * @version $Id$
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BasicModelToolTests extends BaseBookshelfTests
{

    public static class MyBook
    {
        public void setISBN(String isbn) { this.isbn = isbn.toUpperCase(); }
        public String getISBN() { return isbn; }
        private String isbn = null;

    }
    public static class MyPub extends Instance
    {
        public MyPub(Entity entity) { super(entity); }

        public String getAddress()
        {
            return "some address";
        }

    }
    public static class MyAuthor
    {

    }
    public static class MyFactory
    {
        public static MyAuthor createAuthor()
        {
            return new MyAuthor();
        }

    }

    @BeforeClass
    public static void populateDataSource() throws Exception
    {
        BaseBookshelfTests.populateDataSource("bookshelf.sql");
    }

    public @Test void testAction() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Instance oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String title = oneBook.getString("title");
        assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        Action censor = (Action)book.getAttribute("censor");
        assertNotNull(censor);
        long changed = censor.perform(oneBook);
        assertEquals(1, changed);
        oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String censored = oneBook.getString("title");
        assertEquals("** censored **", censored);
        oneBook.perform("rename", title);
        oneBook = book.fetch(1);
        assertNotNull(oneBook);
        assertEquals(title, oneBook.getString("title"));
    }

    public @Test void testBean() throws Exception
    {
        Properties props = new Properties();
        props.load(getResourceReader(Model.MODALITY_DEFAULTS_PATH));
        props.put("model.reverse", "extended");
        props.put("model.credentials.user", "sa");
        props.put("model.credentials.password", "");
        props.put("model.database", "jdbc:hsqldb:.");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");
        props.put("model.instances.classes.book", MyBook.class);
        props.put("model.instances.classes.publisher", MyPub.class);
        props.put("model.instances.factory", MyFactory.class);
        Model model = new Model().configure(props).initialize();
        Instance book = model.getEntity("book").fetch(1);
        assertNotNull(book);
        Object bookId = book.get("book_id");
        assertNotNull(bookId);
        assertEquals(1, bookId);
        assertTrue(book instanceof WrappingInstance);
        assertNull(book.put("ISBN", "lowercase characters"));
        assertEquals("LOWERCASE CHARACTERS", book.get("ISBN"));
        Instance publisher = book.retrieve("publisher");
        assertTrue(publisher instanceof MyPub);
        String address = ((MyPub)publisher).getAddress();
        assertNotNull(address);
        assertEquals("some address", address);
        Instance author = model.getEntity("author").fetch(1);
        assertNotNull(author);
        assertTrue(author instanceof WrappingInstance);
        Object wrapped = ((WrappingInstance)author).unwrap(MyAuthor.class);
        assertNotNull(wrapped);
    }

    public @Test void testBadTransaction() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Instance oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String title = oneBook.getString("title");
        assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        try
        {
            long count = oneBook.perform("bad_transaction");
            fail("expecting SQLException for bad transaction");
        }
        catch (SQLException sqle)
        {
        }
        oneBook.refresh();
        String newtitle = oneBook.getString("title");
        assertEquals(title, newtitle);
    }

    public @Test void testBasicFetch() throws Exception
    {
        Map identMapping = new HashMap();
        identMapping.put("*", "lowercase");
        identMapping.put("*.*", "lowercase");
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.getIdentifiersFilters().addMappings(identMapping);
        model.initialize(getResourceReader("test_init_model.xml"));
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Instance oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String title = oneBook.getString("title");
        assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        Instance otherBook = book.fetch(1);
        assertEquals(oneBook, otherBook);
    }

    public @Test void testCollision() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties modelProps = new Properties();
        modelProps.put("model.datasource", dataSource);
        modelProps.put("model.reverse", "extended");
        modelProps.put("model.identifiers.mapping.*", "lowercase");
        modelProps.put("model.identifiers.mapping.*.*", "lowercase");
        modelProps.put("model.identifiers.mapping.*.*_id", "/^.*_id/id/");
        modelProps.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        Model model = new Model().configure(modelProps);
        try
        {
            model.initialize();
            fail("initialization should throw");
        }
        catch (ConfigurationException e)
        {
            assertEquals("column name collision: book.id mapped on BOOK.BOOK_ID and on BOOK.PUBLISHER_ID", e.getMessage());
        }
    }

    public @Test void testCount() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_init_model.xml"));
        Entity authors = model.getEntity("author");
        assertNotNull(authors);
        long count = authors.getCount();
        assertEquals(2l, count);
    }

    public @Test void testCustomEntity() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.FULL);
        model.initialize(getResourceReader("test_custom_entity.xml"));

        // first method: using newInstance
        Entity blobEntity = model.getEntity("blob");
        assertNotNull(blobEntity);
        Instance blob = blobEntity.newInstance();
        assertNotNull(blob);
        assertEquals("custom entity does not match", blobEntity, blob.getEntity());
        blob.put("text", "%Life%");
        Iterator<Instance> related = blob.query("related_books");
        assertTrue("should find something", related.hasNext());
        Instance book = related.next();
        assertFalse("but only one book", related.hasNext());
        assertNotNull(book);
        assertEquals("The Astonishing Life of Duncan Moonwalker", book.getString("title"));

        // second method: using row attribute
        blob = model.retrieve("get_blob", "%Title%");
        assertNotNull(blob);
        assertEquals("custom entity does not match", blobEntity, blob.getEntity());
        blob.put("text", "%Life%");
        related = blob.query("related_books");
        assertTrue("should find something", related.hasNext());
        book = related.next();
        assertFalse("but only one book", related.hasNext());
        assertNotNull(book);
        assertEquals("The Astonishing Life of Duncan Moonwalker", book.getString("title"));
    }

    public @Test void testGeneratedColumns() throws Exception
    {
        Model model = new Model();
        DataSource dataSource = getDataSource();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        try
        {
            model.perform("new_book", "My Recipies");
        }
        finally
        {
            model.perform("cleanup_books");
        }
    }

    public @Test void testGeneratedColumns2() throws Exception
    {
        Model model = new Model();
        DataSource dataSource = getDataSource();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        try
        {
            Instance book = model.getEntity("book").newInstance();
            book.put("title", "My Recipies");
            book.put("publisher_id", 1);
            book.insert();
        }
        finally
        {
            model.perform("cleanup_books");
        }
    }

    public @Test void testGoodTransaction() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Instance oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String title = oneBook.getString("title");
        assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        long count = oneBook.perform("good_transaction");
        assertEquals(3, count);
        oneBook.refresh();
        String newtitle = oneBook.getString("title");
        assertEquals(title, newtitle);
    }

    public @Test void testInputFilter() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties props = new Properties();
        props.put("model.datasource", dataSource);
        props.put("model.reverse", "tables");
        props.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");
        Filter calendar_to_time = x -> ((Calendar)x).getTime();
        props.put("model.filters.write.java.util.Calendar", calendar_to_time);

        Model model = new Model().configure(props).initialize();
        Instance book = model.getEntity("book").fetch(1);
        Date date = (Date)book.get("published");
        assertNotNull(date);
        DateFormat ymd = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("2018-05-09", ymd.format(date));
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2008, 8, 8);
        book.put("published", cal);
        book.update();
        book.refresh();
        assertEquals("2008-09-08", ymd.format(book.get("published")));
        book.put("published", date);
        book.update();
        book.refresh();
        assertEquals("2018-05-09", ymd.format(book.get("published")));
    }

    public @Test void testJdbc() throws Exception
    {
        Model model = new Model().setDatabaseURL("jdbc:hsqldb:.");
        model.getCredentials().setUser("sa");
        model.getCredentials().setPassword("");
        model.initialize();
    }


    public @Test void testKeysComparison() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties props = new Properties();
        props.put("model.datasource", dataSource);
        props.put("model.reverse", "full");
        props.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");

        Model model = new Model().configure(props).initialize();

        Instance book = model.getEntity("book").fetch(1);
        Map<String, Serializable> values = new HashMap<>();
        values.put("book_id", "1");
        book.putAll(values);
        book.update();
        values.put("book_id", 1452345);
        book.putAll(values);
        try
        {
            book.update();
            fail("Update should be forbidden if PK did change");
        }
        catch (IllegalStateException ise)
        {
            assertEquals("instance must be persisted", ise.getMessage());
        }
    }

    public @Test void testLastInsertId() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.FULL);
        model.initialize(getResourceReader("test_action.xml"));
        Entity author = model.getEntity("author");
        assertNotNull(author);
        Instance oneAuthor = author.newInstance();
        oneAuthor.put("name", "New Writer");
        oneAuthor.insert();
        Serializable id = oneAuthor.get("author_id");
        assertNotNull(id);
        assertTrue(id instanceof Long);
        long longId = (Long)id;
        assertEquals(3, longId);
    }

    public @Test void testMixedParams() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.FULL);
        model.initialize(getResourceReader("test_mixed_params.xml"));
        for (int test = 1; test < 3; ++test)
        {
            Instance book = model.getEntity("book").fetch(1);
            assertNotNull(book);
            Iterator<Instance> it = book.query("find_similar_books_" + test, "2018-01-01", "2018-12-01");
            assertNotNull(it);
            Instance similarBook = it.next();
            assertNotNull(similarBook);
            assertEquals(book, similarBook);
        }
    }

    public @Test void testModelInit() throws Exception
    {
        // test model
        Model model = new Model();
        model.setDataSource(getDataSource());
        model.initialize(getResource("test_init_model.xml"));
        assertEquals(Model.WriteAccess.JAVA, model.getWriteAccess());

        // test entities
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Entity author = model.getEntity("author");
        assertNotNull(author);

        // test attributes
        Attribute countAuthors = book.getAttribute("count_authors");
        assertNotNull(countAuthors);
        assertTrue(countAuthors instanceof ScalarAttribute);
        Attribute countBooks = model.getAttribute("count_books");
        assertNotNull(countBooks);
        assertTrue(countBooks instanceof ScalarAttribute);
        Attribute authors = book.getAttribute("authors");
        assertNotNull(author);
        assertTrue(authors instanceof RowsetAttribute);
    }

    public @Test void testObfuscation() throws Exception
    {
        Properties props = new Properties();
        props.load(getResourceReader(Model.MODALITY_DEFAULTS_PATH));
        props.put("model.reverse", "extended");
        props.put("model.credentials.user", "sa");
        props.put("model.credentials.password", "");
        props.put("model.database", "jdbc:hsqldb:.");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*_id", "snake_to_camel");
        props.put("model.filters.read.book.book_id", "obfuscate");
        props.put("model.filters.write.book.book_id", "deobfuscate_strings");
        props.put("model.filters.read.author.author_id", "obfuscate");
        props.put("model.filters.write.author.author_id", "deobfuscate");
        Model model = new Model().configure(props).initialize();
        Instance book = model.getEntity("book").fetch(1);
        assertNotNull(book);
        Object bookId = book.get("bookId");
        assertNotNull(bookId);
        assertTrue(bookId instanceof String && ((String)bookId).length() > 5);
        Iterator<Instance> authors = book.query("authors");
        assertNotNull(authors);
        Instance author1 = authors.next();
        Instance author2 = authors.next();
        assertFalse(authors.hasNext());
        assertNotNull(author1);
        assertNotNull(author2);
        Object authorId = author1.get("authorId");
        assertNotNull(authorId);
        assertTrue(authorId instanceof String && ((String)authorId).length() > 5);
        Instance again = author1.query("books").next();
        assertNotNull(again);
        assertEquals(book.get("bookId"), again.get("bookId"));
        try
        {
            Instance author = model.getEntity("author").fetch(1);
            fail("SQLException expected");
        }
        catch (SQLException sqle)
        {
            assertEquals("data exception: invalid character value for cast", sqle.getMessage());
        }
    }

    public @Test void testRealData() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.initialize(getResourceReader("test_init_model.xml"));
        ScalarAttribute countBooks = (ScalarAttribute)model.getAttribute("count_books");
        long books = countBooks.getLong();
        assertEquals(books, 1);
    }

    public @Test void testReverseColumns() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_init_model.xml"));
        Entity book = model.getEntity("book");
        Collection<Entity.Column> columns = book.getColumns();
        assertNotNull(columns);
        String allCols = columns.stream().map(c -> c.name).collect(Collectors.joining(","));
        assertEquals("book_id,title,published,publisher_id", allCols);
        List<Entity.Column> pk = book.getPrimaryKey();
        assertNotNull(pk);
        assertEquals(1, pk.size());
        assertEquals("book_id", pk.get(0).name);
    }

    public @Test void testReverseJoins() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.JOINS);
        model.getIdentifiersFilters().setInflector("org.atteo.evo.inflector.English");
        model.getIdentifiersFilters().addMappings("lowercase");
        model.initialize(getResourceReader("test_minimal_model.xml"));

        // test upstream attribute
        Entity bookEntity = model.getEntity("book");
        assertNotNull(bookEntity);
        Attribute bookPublisher = bookEntity.getAttribute("publisher");
        assertNotNull(bookPublisher);
        assertTrue(bookPublisher instanceof RowAttribute);
        Instance book = bookEntity.fetch(1);
        assertNotNull(book);
        Instance publisher = book.retrieve("publisher");
        assertNotNull(publisher);
        assertEquals("Green Penguin Books", publisher.getString("name"));

        // test downstream attribute
        Entity publisherEntity = model.getEntity("publisher");
        assertNotNull(publisherEntity);
        Instance firstPublisher = publisherEntity.fetch(1);
        assertNotNull(firstPublisher);
        Iterator<Instance> books = firstPublisher.query("books");
        assertNotNull(books);
        assertTrue(books.hasNext());
        assertNotNull(books.next());
    }

    public @Test void testSuccessfulManualTransaction() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        ModelRunnable runnable = new ModelRunnable()
        {
            @Override
            public void run() throws SQLException
            {
                Entity book = model.getEntity("book");
                assertNotNull(book);
                Instance oneBook = book.fetch(1);
                assertNotNull(oneBook);
                String title = oneBook.getString("title");
                assertEquals("The Astonishing Life of Duncan Moonwalker", title);
                oneBook.put("title", "Another Title");
                oneBook.update();
                String newTitle = oneBook.getString("title");
                assertEquals("Another Title", newTitle);
                oneBook.put("title", title);
                oneBook.update();
                title = oneBook.getString("title");
                assertEquals("The Astonishing Life of Duncan Moonwalker", title);
            }
        };
        model.attempt(runnable);
    }

    public @Test void testUnsuccessfulManualTransaction() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        ModelRunnable runnable = new ModelRunnable()
        {
            @Override
            public void run() throws SQLException
            {
                Entity book = model.getEntity("book");
                assertNotNull(book);
                Instance oneBook = book.fetch(1);
                assertNotNull(oneBook);
                String title = oneBook.getString("title");
                assertEquals("The Astonishing Life of Duncan Moonwalker", title);
                oneBook.put("title", "Another Title");
                oneBook.update();
                String newTitle = oneBook.getString("title");
                assertEquals("Another Title", newTitle);
                Instance anotherBook = book.newInstance(oneBook);
                anotherBook.put("title", null);
                anotherBook.insert(); // will fail
            }
        };
        try
        {
            model.attempt(runnable);
            fail("should throw");
        }
        catch (SQLException sqle)
        {
            Entity book = model.getEntity("book");
            assertNotNull(book);
            Instance oneBook = book.fetch(1);
            assertNotNull(oneBook);
            String title = oneBook.getString("title");
            assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        }
    }

    public @Test void testUpsert() throws Exception
    {
        DataSource dataSource = getDataSource();
        Model model = new Model();
        model.setDataSource(dataSource);
        model.setReverseMode(Model.ReverseMode.COLUMNS);
        model.initialize(getResourceReader("test_action.xml"));
        Entity book = model.getEntity("book");
        assertNotNull(book);
        Instance oneBook = book.fetch(1);
        assertNotNull(oneBook);
        String title = oneBook.getString("title");
        assertEquals("The Astonishing Life of Duncan Moonwalker", title);
        oneBook.put("title", "foo");
        oneBook.upsert();
        assertEquals("foo", book.fetch(1).getString("title"));
        oneBook = book.newInstance(oneBook);
        oneBook.put("title", title);
        oneBook.upsert();
        assertFalse(oneBook.isDirty());
        assertEquals(title, book.fetch(1).getString("title"));
    }

    public @Test void testValueFilters() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties props = new Properties();
        props.put("model.datasource", dataSource);
        props.put("model.reverse", "full");
        props.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");
        // allow html in book title
        props.put("model.filters.read.book.title", "escape_html");
        // disallow html in names
        props.put("model.filters.write.*.name", "no_html");
        props.put("model.filters.read.book.title", "escape_html");

        Model model = new Model().configure(props).initialize();

        Instance book = model.getEntity("book").fetch(1);
        Instance publisher = book.retrieve("publisher");

        // test no_html
        publisher.put("name", "<forbidden>");
        try
        {
            publisher.update();
            fail("expecting SQLException for invalid character");
        }
        catch (SQLException sqle) {}

        // test escape_html
        String prevTitle = book.getString("title");
        String title = "the common <way> is \"hidden\"";
        String escapedTitle = StringEscapeUtils.escapeHtml4(title);
        book.put("title", title);
        book.update();
        book.refresh();
        Serializable digestedTitle = book.get("title");
        assertEquals("did not find an HtmlEscaped object", ValueFilters.HtmlEscaped.class, digestedTitle.getClass());
        String disgestedTitle = book.getString("title");
        assertEquals(escapedTitle, disgestedTitle);
        book.put("title", prevTitle);
        book.update();
        book.refresh();
        assertEquals("something's very wrong", prevTitle, book.getString("title"));
    }

    public @Test void testValueFiltersRelaxing() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties props = new Properties();
        props.put("model.datasource", dataSource);
        props.put("model.reverse", "full");
        props.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");

        // disallow html everywhere
        props.put("model.filters.write.*.*", "no_html");
        // allow html in book title
        props.put("model.filters.write.book.title", "-no_html");
        props.put("model.filters.read.book.title", "escape_html");

        Model model = new Model().configure(props).initialize();

        Instance book = model.getEntity("book").fetch(1);
        Instance publisher = book.retrieve("publisher");

        // test no_html
        publisher.put("name", "<forbidden>");
        try
        {
            publisher.update();
            fail("expecting SQLException for invalid character");
        }
        catch (SQLException sqle) {}

        // test escape_html
        String prevTitle = book.getString("title");
        String title = "the common <way> is \"hidden\"";
        String escapedTitle = StringEscapeUtils.escapeHtml4(title);
        book.put("title", title);
        book.update();
        book.refresh();
        Serializable digestedTitle = book.get("title");
        assertEquals("did not find an HtmlEscaped object", ValueFilters.HtmlEscaped.class, digestedTitle.getClass());
        String disgestedTitle = book.getString("title");
        assertEquals(escapedTitle, disgestedTitle);
        book.put("title", prevTitle);
        book.update();
        book.refresh();
        assertEquals("something's very wrong", prevTitle, book.getString("title"));
    }

    public @Test void testWithoutInputFilter() throws Exception
    {
        DataSource dataSource = getDataSource();
        Properties props = new Properties();
        props.put("model.datasource", dataSource);
        props.put("model.reverse", "tables");
        props.put("model.identifiers.inflector", "org.atteo.evo.inflector.English");
        props.put("model.identifiers.mapping.*", "lowercase");
        props.put("model.identifiers.mapping.*.*", "lowercase");

        Model model = new Model().configure(props).initialize();
        Instance book = model.getEntity("book").fetch(1);
        Date date = (Date)book.get("published");
        assertNotNull(date);
        DateFormat ymd = new SimpleDateFormat("yyyy-MM-dd");
        assertEquals("2018-05-09", ymd.format(date));
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2008, 8, 8);
        book.put("published", cal);
        try
        {
            book.update();
            fail("should throw when hsqldb receives a Calendar");
        }
        catch (SQLException sqle)
        {
            assertEquals("incompatible data type in conversion", sqle.getMessage());
        }
    }

}
