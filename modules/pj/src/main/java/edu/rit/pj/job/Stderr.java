//******************************************************************************
//
// File:    Stderr.java
// Package: edu.rit.pj.job
// Unit:    Class edu.rit.pj.job.Stderr
//
// This Java source file is copyright (C) 2010 by Alan Kaminsky. All rights
// reserved. For further information, contact the author, Alan Kaminsky, at
// ark@cs.rit.edu.
//
// This Java source file is part of the Parallel Java Library ("PJ"). PJ is free
// software; you can redistribute it and/or modify it under the terms of the GNU
// General Public License as published by the Free Software Foundation; either
// version 3 of the License, or (at your option) any later version.
//
// PJ is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
// A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// A copy of the GNU General Public License is provided in the file gpl.txt. You
// may also obtain a copy of the GNU General Public License on the World Wide
// Web at http://www.gnu.org/licenses/gpl.html.
//
//******************************************************************************
package edu.rit.pj.job;

/**
 * Enum Stderr enumerates how the standard error stream of a {@linkplain Job} is
 * redirected.
 *
 * @author Alan Kaminsky
 * @version 07-Oct-2010
 */
enum Stderr {

    /**
     * The standard error is not redirected.
     */
    NONE,
    /**
     * The standard error is stored in a file. The file is created if it does
     * not exist. The file is overwritten if it does exist.
     */
    FILE,
    /**
     * The standard error is added to the end of a file. The file is created if
     * it does not exist.
     */
    FILE_APPEND,
    /**
     * The standard error goes to the same place as the standard output.
     */
    STDOUT,

}
