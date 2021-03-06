// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2012, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2012, David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.cloudcoder.app.server.persist;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

import org.cloudcoder.app.shared.model.Change;
import org.cloudcoder.app.shared.model.ConfigurationSetting;
import org.cloudcoder.app.shared.model.ConfigurationSettingName;
import org.cloudcoder.app.shared.model.Course;
import org.cloudcoder.app.shared.model.CourseRegistration;
import org.cloudcoder.app.shared.model.CourseRegistrationType;
import org.cloudcoder.app.shared.model.Event;
import org.cloudcoder.app.shared.model.ModelObjectSchema;
import org.cloudcoder.app.shared.model.Problem;
import org.cloudcoder.app.shared.model.SubmissionReceipt;
import org.cloudcoder.app.shared.model.Term;
import org.cloudcoder.app.shared.model.TestCase;
import org.cloudcoder.app.shared.model.TestResult;
import org.cloudcoder.app.shared.model.User;

/**
 * Create the webapp database, using the metadata information
 * specified by the model classes.
 * 
 * @author David Hovemeyer
 */
public class CreateWebappDatabase {
	static final boolean DEBUG = false;
	
	/**
	 * Array with all of the tables needed to run the webapp.
	 */
	public static final ModelObjectSchema<?>[] TABLES = {
		Change.SCHEMA,
		ConfigurationSetting.SCHEMA,
		Course.SCHEMA,
		CourseRegistration.SCHEMA,
		Event.SCHEMA,
		Problem.SCHEMA,
		SubmissionReceipt.SCHEMA,
		Term.SCHEMA,
		TestCase.SCHEMA,
		TestResult.SCHEMA,
		User.SCHEMA,
	};
	
	public static void main(String[] args) throws Exception {
		ConfigurationUtil.configureLog4j();
		
		try {
			createWebappDatabase();
		} catch (SQLException e) {
			// Handle SQLException by printing an error message:
			// these are likely to be meaningful to the user
			// (for example, can't create the database because
			// it already exists.)
		    e.printStackTrace(System.err);
			System.err.println("Database error: " + e.getMessage());
		}
	}

	private static void createWebappDatabase() throws ClassNotFoundException,
			FileNotFoundException, IOException, SQLException {
		System.out.println("Please enter some information needed to configure the Cloudcoder");
		System.out.println("database.  (Hit enter to accept a default value, if there is one.)");
		
		Scanner keyboard = new Scanner(System.in);
		
		String ccUserName = ConfigurationUtil.ask(keyboard, "Enter a username for your CloudCoder account: ");
		String ccPassword = ConfigurationUtil.ask(keyboard, "Enter a password for your CloudCoder account");
		String ccFirstname = ConfigurationUtil.ask(keyboard, "What is your first name?");
		String ccLastname= ConfigurationUtil.ask(keyboard, "What is your last name?");
		String ccEmail= ConfigurationUtil.ask(keyboard, "What is your email address?");
		String ccWebsite = ConfigurationUtil.ask(keyboard, "What is your website URL?");
		String ccInstitutionName = ConfigurationUtil.ask(keyboard, "What is your institution name (e.g, 'Unseen University')?");
		String ccRepoUrl = ConfigurationUtil.ask(keyboard, "Enter the URL of the exercise repository", "https://cloudcoder.org/repo");
		
		Class.forName("com.mysql.jdbc.Driver");

		Properties config = DBUtil.getConfigProperties();
		
		Connection conn = DBUtil.connectToDatabaseServer(config, "cloudcoder.db");
		
		String dbName = config.getProperty("cloudcoder.db.databaseName");
		if (dbName == null) {
			throw new IllegalStateException("configuration properties do not define cloudcoder.db.databaseName");
		}
		
		System.out.println("Creating database");
		DBUtil.createDatabase(conn, dbName);
		
		conn.close();
		
		// Reconnect to the newly-created database
		conn = DBUtil.connectToDatabase(config, "cloudcoder.db");
		
		// Create schema version table
		System.out.println("Creating schema version table...");
		SchemaUtil.createSchemaVersionTableIfNeeded(conn, TABLES);
		
		// Create tables and indexes
		for (ModelObjectSchema<?> schema : TABLES) {
			createTable(conn, schema);
		}
		
		// Create initial database contents
		
		// Set institution name (and any other configuration settings)
		System.out.println("Adding configuration settings...");

		DBUtil.storeConfigurationSetting(conn, ConfigurationSettingName.PUB_TEXT_INSTITUTION, ccInstitutionName);
		DBUtil.storeConfigurationSetting(conn, ConfigurationSettingName.PUB_REPOSITORY_URL, ccRepoUrl);
		
		// Terms
		System.out.println("Creating terms...");
		CreateWebappDatabase.storeTerm(conn, "Winter", 0);
		CreateWebappDatabase.storeTerm(conn, "Spring", 1);
		CreateWebappDatabase.storeTerm(conn, "Summer", 2);
		CreateWebappDatabase.storeTerm(conn, "Summer 1", 3);
		CreateWebappDatabase.storeTerm(conn, "Summer 2", 4);
		Term fall = CreateWebappDatabase.storeTerm(conn, "Fall", 5);
		
		// Create an initial demo course
		System.out.println("Creating demo course...");
		int courseId = CreateSampleData.createDemoCourse(conn, fall);
		
		// Create an initial user
		System.out.println("Creating initial user...");
		int userId = ConfigurationUtil.createOrUpdateUser(conn, 
		        ccUserName, 
		        ccFirstname,
		        ccLastname,
		        ccEmail,
		        ccPassword,
		        ccWebsite);
		
		// Register the user as an instructor in the demo course
		System.out.println("Registering initial user for demo course...");
		ConfigurationUtil.registerUser(conn, userId, courseId, CourseRegistrationType.INSTRUCTOR, 101);
		
		// Create a Problem
		System.out.println("Creating hello, world problem in demo course...");
		Problem problem = new Problem();
		CreateSampleData.populateSampleProblem(problem, courseId);
		DBUtil.storeModelObject(conn, problem);
		Integer problemId = problem.getProblemId();
		
		// Add a TestCase
		System.out.println("Creating test case for hello, world problem...");
		TestCase testCase = new TestCase();
		CreateSampleData.populateSampleTestCase(testCase, problemId);

		DBUtil.storeModelObject(conn, testCase);

		conn.close();
		
		System.out.println("Success!");
	}

	private static<E> void createTable(Connection conn, ModelObjectSchema<E> schema) throws SQLException {
		System.out.println("Creating table " + schema.getDbTableName());
		DBUtil.createTable(conn, schema);
	}

	private static Term storeTerm(Connection conn, String name, int seq) throws SQLException {
		Term term = new Term();
		term.setName(name);
		term.setSeq(seq);

		DBUtil.storeModelObject(conn, term);

		return term;
	}
}
