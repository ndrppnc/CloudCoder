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

package org.cloudcoder.app.server.rpc;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService;
import org.cloudcoder.app.server.persist.Database;
import org.cloudcoder.app.shared.model.ConfigurationSetting;
import org.cloudcoder.app.shared.model.ConfigurationSettingName;
import org.cloudcoder.app.shared.model.Course;
import org.cloudcoder.app.shared.model.CourseAndCourseRegistration;
import org.cloudcoder.app.shared.model.CourseRegistration;
import org.cloudcoder.app.shared.model.CourseRegistrationType;
import org.cloudcoder.app.shared.model.CloudCoderAuthenticationException;
import org.cloudcoder.app.shared.model.OperationResult;
import org.cloudcoder.app.shared.model.Pair;
import org.cloudcoder.app.shared.model.Problem;
import org.cloudcoder.app.shared.model.ProblemAndSubmissionReceipt;
import org.cloudcoder.app.shared.model.ProblemAndTestCaseList;
import org.cloudcoder.app.shared.model.ProblemAuthorship;
import org.cloudcoder.app.shared.model.ProblemList;
import org.cloudcoder.app.shared.model.SubmissionReceipt;
import org.cloudcoder.app.shared.model.Term;
import org.cloudcoder.app.shared.model.TestCase;
import org.cloudcoder.app.shared.model.User;
import org.cloudcoder.app.shared.model.json.JSONConversion;
import org.cloudcoder.app.shared.model.json.ReflectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * RPC servlet for accessing courses and problems.
 */
public class GetCoursesAndProblemsServiceImpl extends RemoteServiceServlet
		implements GetCoursesAndProblemsService {
	private static final long serialVersionUID = 1L;
	private static final Logger logger=LoggerFactory.getLogger(GetCoursesAndProblemsServiceImpl.class);

	@Override
	public Course[] getCourses() throws CloudCoderAuthenticationException {
		// make sure the client has authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		
		logger.info("Loading courses for user " + user.getUsername());
		
		List<? extends Object[]> resultList = Database.getInstance().getCoursesForUser(user);
		
		Course[] result = new Course[resultList.size()];
		int count = 0;
		for (Object[] tuple : resultList) {
			Course course = (Course) tuple[0];
			Term term = (Term) tuple[1];
			course.setTerm(term);
			result[count++] = course;
			
			logger.info("Found course: " + course.getName() + ": " + course.getTitle());
		}
		
		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#getCourseAndCourseRegistrations()
	 */
	@Override
	public CourseAndCourseRegistration[] getCourseAndCourseRegistrations() throws CloudCoderAuthenticationException {
		// make sure the client has authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		
		logger.info("Loading courses and registrations for user " + user.getUsername());
		
		List<? extends Object[]> resultList = Database.getInstance().getCoursesForUser(user);
		
		CourseAndCourseRegistration[] result = new CourseAndCourseRegistration[resultList.size()];
		int count = 0;
		for (Object[] tuple : resultList) {
			Course course = (Course) tuple[0];
			Term term = (Term) tuple[1];
			course.setTerm(term);
			CourseRegistration reg = (CourseRegistration) tuple[2];
			
			CourseAndCourseRegistration obj = new CourseAndCourseRegistration();
			obj.setCourse(course);
			obj.setCourseRegistration(reg);
			
			result[count++] = obj;
		}
		
		return result;
	}

	@Override
	public Problem[] getProblems(Course course) throws CloudCoderAuthenticationException {
		// Make sure user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());

		List<Problem> resultList = Database.getInstance().getProblemsInCourse(user, course).getProblemList();
		for (Problem p : resultList) {
			logger.info(p.getTestname() + " - " + p.getBriefDescription());
		}
		
		return resultList.toArray(new Problem[resultList.size()]);
	}
	
	public Problem[] getProblemsForUser(Course course,int userId) throws CloudCoderAuthenticationException {
		// Make sure user is authenticated
		User user = Database.getInstance().getUserGivenId(userId);
		
		List<Problem> resultList = Database.getInstance().getProblemsInCourse(user, course).getProblemList();
		for (Problem p : resultList) {
			logger.warn(p.getTestname() + " - " + p.getBriefDescription());
		}
		
		return resultList.toArray(new Problem[resultList.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#getProblemAndSubscriptionReceipts(org.cloudcoder.app.shared.model.Course)
	 */
	@Override
	public ProblemAndSubmissionReceipt[] getProblemAndSubscriptionReceipts(
			Course course) throws CloudCoderAuthenticationException {
		// Make sure user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		
		logger.info("getting submission receipts for authenticated user "+user.getUsername());
		
		List<ProblemAndSubmissionReceipt> resultList = Database.getInstance().getProblemAndSubscriptionReceiptsInCourse(user, course);
		return resultList.toArray(new ProblemAndSubmissionReceipt[resultList.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#getProblemAndSubscriptionReceipts(org.cloudcoder.app.shared.model.Course)
	 */
	@Override
	public ProblemAndSubmissionReceipt[] getProblemAndSubscriptionReceipts(
			Course course, User user) throws CloudCoderAuthenticationException {

		logger.warn("yay! getting submission receipts for user "+user.getUsername());
		
		List<ProblemAndSubmissionReceipt> resultList = new LinkedList<ProblemAndSubmissionReceipt>();
		ProblemList problems = Database.getInstance().getProblemsInCourse(user, course);
		for(Problem p : problems.getProblemList()){
			List<Pair<User, SubmissionReceipt>> e = Database.getInstance().getBestSubmissionReceipts(course, p.getProblemId());
			for(Pair<User,SubmissionReceipt> pair : e){
				if(pair.getLeft().getId() == user.getId()){
					resultList.add(new ProblemAndSubmissionReceipt(p,pair.getRight()));
				}
			}
		}
		
		//List<ProblemAndSubmissionReceipt> resultList = Database.getInstance().getBestSubmissionReceipts(course, problemId).getProblemAndSubscriptionReceiptsInCourse(user, course);
		return resultList.toArray(new ProblemAndSubmissionReceipt[resultList.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#getTestCasesForProblem(int)
	 */
	@Override
	public TestCase[] getTestCasesForProblem(int problemId) throws CloudCoderAuthenticationException {
		// Make sure user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());

		return Database.getInstance().getTestCasesForProblem(user, problemId);
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#storeProblemAndTestCaseList(org.cloudcoder.app.shared.model.ProblemAndTestCaseList)
	 */
	@Override
	public ProblemAndTestCaseList storeProblemAndTestCaseList(ProblemAndTestCaseList problemAndTestCaseList, Course course)
			throws CloudCoderAuthenticationException {
		// Make sure user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		
		// Store in database
		Database.getInstance().storeProblemAndTestCaseList(problemAndTestCaseList, course, user);

		// Return updated object
		return problemAndTestCaseList;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.app.client.rpc.GetCoursesAndProblemsService#submitExercise(org.cloudcoder.app.shared.model.ProblemAndTestCaseList, java.lang.String, java.lang.String)
	 */
	@Override
	public OperationResult submitExercise(ProblemAndTestCaseList exercise, String repoUsername, String repoPassword)
		throws CloudCoderAuthenticationException  {
		System.out.println("Sharing exercise: " + exercise.getProblem().getTestname());
		
		ConfigurationSetting repoUrlSetting = Database.getInstance().getConfigurationSetting(ConfigurationSettingName.PUB_REPOSITORY_URL);
		if (repoUrlSetting == null) {
			return new OperationResult(false, "URL of exercise repository is not configured");
		}
		String repoUrl = repoUrlSetting.getValue();
		if (repoUrl.endsWith("/")) {
			repoUrl = repoUrl.substring(0, repoUrl.length()-1);
		}
		
		HttpPost post = new HttpPost(repoUrl + "/exercisedata");
		
		// Encode an Authorization header using the provided repository username and password.
		String authHeaderValue =
				"Basic " +
				DatatypeConverter.printBase64Binary((repoUsername + ":" + repoPassword).getBytes(Charset.forName("UTF-8")));
		//System.out.println("Authorization: " + authHeaderValue);
		post.addHeader("Authorization", authHeaderValue);
		
		// Convert the exercise to a JSON string
		StringEntity entity;
		StringWriter sw = new StringWriter();
		try {
			JSONConversion.writeProblemAndTestCaseData(exercise, sw);
			entity = new StringEntity(sw.toString(), ContentType.create("application/json", "UTF-8"));
		} catch (IOException e) {
			return new OperationResult(false, "Could not convert exercise to JSON: " + e.getMessage());
		}
		post.setEntity(entity);
		
		// POST the exercise to the repository
		HttpClient client = new DefaultHttpClient();
		try {
			HttpResponse response = client.execute(post);
		
			StatusLine statusLine = response.getStatusLine();
			
			if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
				return new OperationResult(true, "Exercise successfully published to the repository - thank you!");
			} else if (statusLine.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
				return new OperationResult(false, "Authentication with repository failed - incorrect username/password?");
			} else {
				return new OperationResult(false, "Failed to publish exercise to repository: " + statusLine.getReasonPhrase());
			}
		} catch (ClientProtocolException e) {
			return new OperationResult(false, "Error sending exercise to repository: " + e.getMessage());
		} catch (IOException e) {
			return new OperationResult(false, "Error sending exercise to repository: " + e.getMessage());
		} finally {
			client.getConnectionManager().shutdown();
		}
	}
	
	@Override
	public ProblemAndTestCaseList importExercise(Course course, String exerciseHash) throws CloudCoderAuthenticationException {
		if (course == null || exerciseHash == null) {
			throw new IllegalArgumentException();
		}
		
		// Make sure a user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		
		// Find user's registration in the course: if user is not instructor,
		// import is not allowed
		CourseRegistration reg = Database.getInstance().findCourseRegistration(user, course);
		if (reg.getRegistrationType().ordinal() < CourseRegistrationType.INSTRUCTOR.ordinal()) {
			throw new CloudCoderAuthenticationException("Only an instructor can import a problem in a course");
		}
		
		// Attempt to load the problem from the exercise repository.
		ConfigurationSetting repoUrlSetting = Database.getInstance().getConfigurationSetting(ConfigurationSettingName.PUB_REPOSITORY_URL);
		if (repoUrlSetting == null) {
			logger.error("Repository URL configuration setting is not set");
			return null;
		}
		
		// GET the exercise from the repository
		HttpGet get = new HttpGet(repoUrlSetting.getValue() + "/exercisedata/" + exerciseHash);
		ProblemAndTestCaseList exercise = null;
		
		HttpClient client = new DefaultHttpClient();
		try {
			HttpResponse response = client.execute(get);
			
			HttpEntity entity = response.getEntity();
			
			ContentType contentType = ContentType.getOrDefault(entity);
			Reader reader = new InputStreamReader(entity.getContent(), contentType.getCharset());
			
			exercise = new ProblemAndTestCaseList();
			exercise.setTestCaseList(new TestCase[0]);
			JSONConversion.readProblemAndTestCaseData(exercise,
					ReflectionFactory.forClass(Problem.class),
					ReflectionFactory.forClass(TestCase.class),
					reader);
			
			// Set the course id
			exercise.getProblem().setCourseId(course.getId());
		} catch (IOException e) {
			logger.error("Error importing exercise from repository", e);
			return null;
		} finally {
			client.getConnectionManager().shutdown();
		}
		
		// Set "when assigned" and "when due" to reasonable default values
		long now = System.currentTimeMillis();
		exercise.getProblem().setWhenAssigned(now);
		exercise.getProblem().setWhenDue(now + 48L*60L*60L*1000L);
		
		// Set problem authorship as IMPORTED
		exercise.getProblem().setProblemAuthorship(ProblemAuthorship.IMPORTED);
		
		// For IMPORTED problems, parent_hash is actually the hash of the problem
		// itself.  If the problem is modified (and the authorship changed
		// to IMPORTED_AND_MODIFIED), then the (unchanged) parent_hash value
		// really does reflect the "parent" problem.
		exercise.getProblem().setParentHash(exerciseHash);
		
		// Store the exercise in the database
		exercise = Database.getInstance().storeProblemAndTestCaseList(exercise, course, user);
		
		return exercise;
	}
	
	@Override
	public OperationResult deleteProblem(Course course, Problem problem) throws CloudCoderAuthenticationException {
		// Make sure a user is authenticated
		User user = ServletUtil.checkClientIsAuthenticated(getThreadLocalRequest());
		boolean result = Database.getInstance().deleteProblem(user, course, problem);
		return new OperationResult(result, result ? "Problem deleted successfully" : "Could not delete problem");
	}
}
