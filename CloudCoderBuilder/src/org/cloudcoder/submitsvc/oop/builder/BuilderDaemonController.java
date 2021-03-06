// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2012, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2012, David H. Hovemeyer <dhovemey@ycp.edu>
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

package org.cloudcoder.submitsvc.oop.builder;

import org.cloudcoder.daemon.DaemonController;
import org.cloudcoder.daemon.IDaemon;

/**
 * {@link DaemonController} implementation for the Builder.
 * Also contains the main method used when the Builder is deployed
 * as an executable jar file.
 * 
 * @author David Hovemeyer
 */
public class BuilderDaemonController extends DaemonController {

	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.DaemonController#getDefaultInstanceName()
	 */
	@Override
	public String getDefaultInstanceName() {
		return "instance";
	}

	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.DaemonController#getDaemonClass()
	 */
	@Override
	public Class<? extends IDaemon> getDaemonClass() {
		return BuilderDaemon.class;
	}
	
	/* (non-Javadoc)
	 * @see org.cloudcoder.daemon.DaemonController#createOptions()
	 */
	@Override
	protected Options createOptions() {
		// Create the stdout log in the "log" directory.
		return new Options() {
			@Override
			public String getStdoutLogFileName() {
				return "log/stdout.log";
			}
		};
	}

	public static void main(String[] args) {
		BuilderDaemonController controller = new BuilderDaemonController();
		controller.exec(args);
	}
}
