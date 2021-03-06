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

package org.cloudcoder.app.server.admin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cloudcoder.app.server.persist.Database;
import org.cloudcoder.app.shared.model.Course;
import org.cloudcoder.app.shared.model.Pair;
import org.cloudcoder.app.shared.model.Problem;
import org.cloudcoder.app.shared.model.ProblemList;
import org.cloudcoder.app.shared.model.ProblemSummary;
import org.cloudcoder.app.shared.model.ProblemSummaryList;
import org.cloudcoder.app.shared.model.SubmissionReceipt;
import org.cloudcoder.app.shared.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVWriter;

/**
 * Servlet to retrieve information about problem submissions.
 * 
 * @author David Hovemeyer
 */
public class Problems extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(Problems.class);

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		ProblemURLInfo problemURLInfo = ProblemURLInfo.fromRequest(req);
		if (problemURLInfo == null) {
			// Should not happen
			logger.error("Could not extract problem info from request pathInfo {}", req.getPathInfo());
			AdminServletUtil.badRequest(resp);
			return;
		}
		
		User user = (User) req.getAttribute(RequestAttributeKeys.USER_KEY);
		Course course = (Course) req.getAttribute(RequestAttributeKeys.COURSE_KEY);
		
		if (problemURLInfo.getProblemId() < 0) {
			summarizeProblems(user, course, resp);
		} else {
			int problemId = problemURLInfo.getProblemId();
			summarizeStudentWorkOnProblem(user, course, problemId, resp);
		}
	}
	
	private static final String[] PROBLEMS_HEADER = {
		"problemId", "testName", "numStudents", "numStarted", "passedAtLeastOneTest", "numCompleted",
	};

	private void summarizeProblems(User user, Course course, HttpServletResponse resp) throws IOException {
		// Just summarize problems available in this course
		
		resp.setContentType("text/csv");
		resp.addHeader("Content-disposition", "attachment;filename=problemsInCourse" + course.getId() + ".csv");
		
		ProblemList problemList = Database.getInstance().getProblemsInCourse(user, course);
		
		ProblemSummaryList problemSummaryList = new ProblemSummaryList();
		for (Problem problem : problemList.getProblemList()) {
			ProblemSummary problemSummary = Database.getInstance().createProblemSummary(problem);
			problemSummaryList.add(problemSummary);
		}

		CSVWriter writer = new CSVWriter(resp.getWriter());
		
		writer.writeNext(PROBLEMS_HEADER);
		
		for (ProblemSummary summary : problemSummaryList.getList()) {
			List<String> entry = new ArrayList<String>();
			entry.add(String.valueOf(summary.getProblemId()));
			entry.add(summary.getTestName());
			entry.add(String.valueOf(summary.getNumStudents()));
			entry.add(String.valueOf(summary.getNumStarted()));
			entry.add(String.valueOf(summary.getNumPassedAtLeastOneTest()));
			entry.add(String.valueOf(summary.getNumCompleted()));
			
			writer.writeNext(entry.toArray(new String[entry.size()]));
		}
	}
	
	private static final String[] BEST_SUBMISSION_HEADER = new String[]{
		"Username","Passed/Total"
	};

	/**
	 * Summarize student work on a particular problem in a particular course.
	 * 
	 * @param user        authenticated user
	 * @param course      the course
	 * @param problemId   the problem id
	 * @param resp        the HttpServletResponse to write to
	 * @throws ServletException 
	 * @throws IOException 
	 */
	private void summarizeStudentWorkOnProblem(User user, Course course, int problemId, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/csv");
		resp.addHeader("Content-disposition", "attachment;filename=course" + course.getId() + "Problem" + problemId + ".csv");

		List<Pair<User, SubmissionReceipt>> bestSubmissions = Database.getInstance().getBestSubmissionReceipts(course, problemId);
		
		CSVWriter writer = new CSVWriter(resp.getWriter());
		
		String problemName = Database.getInstance().getProblem(problemId).getBriefDescription();
		int numTests = Database.getInstance().getTestCasesForProblem(problemId).size();
		
		writer.writeNext(new String[]{course.getName()+" - \""+problemName+"\""});
		writer.writeNext(new String[]{});
		writer.writeNext(BEST_SUBMISSION_HEADER);
		
		for (Pair<User, SubmissionReceipt> pair : bestSubmissions) {
			List<String> entry = new ArrayList<String>();
			entry.add(pair.getLeft().getUsername());
			entry.add(String.valueOf(pair.getRight().getNumTestsPassed()+" out of "+numTests));
			writer.writeNext(entry.toArray(new String[entry.size()]));
		}
	}
	
}
