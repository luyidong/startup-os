/*
 * Copyright 2018 The StartupOS Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.startupos.tools.reviewer.job.sync;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class for determining line number of the file relative to a position in the diff and in the
 * opposite direction. `Line number` is the absolute line number in the file. `Position` is line
 * number in the diff(patch). `Position` is unique for the diff.
 */
// TODO: Add tests to check the correctness of the converting
public class LineNumberConverter {
  /*
  The comment can be on the left side or on the right side.
  `LEFT` means the file from the base branch (usually "master").
  `RIGHT` means the file from the branch with changes we want to merge.
  */
  public enum Side {
    LEFT,
    RIGHT
  }

  // The relationship between position in the diff and line number in file for the left side
  private Map<Integer, Integer> positionToLineNumberLeftSide = new HashMap<>();
  // The relationship between position in the diff and line number in file for the right side
  private Map<Integer, Integer> positionToLineNumberRightSide = new HashMap<>();
  // The relationship between line number in file and position in the diff for the left side
  private Map<Integer, Integer> lineNumberToPositionLeftSide = new HashMap<>();
  // The relationship between line number in file and position in the diff for the right side
  private Map<Integer, Integer> lineNumberToPositionRightSide = new HashMap<>();

  /**
   * LineNumberConverter class constructor.
   *
   * @param diffPatchStr `patch`(diff) of GitHub Pull Request file in string format
   */
  public LineNumberConverter(String diffPatchStr) {
    processDiffPatch(diffPatchStr);
  }

  /**
   * Returns the absolute line number in the file
   *
   * @param position is line number in the diff(patch)
   * @param side the side(LEFT or RIGHT) where the comment is added
   */
  public int getLineNumber(int position, Side side) {
    return side.equals(Side.LEFT)
        ? positionToLineNumberLeftSide.get(position)
        : positionToLineNumberRightSide.get(position);
  }

  /**
   * Returns the position in the diff(patch)
   *
   * @param lineNumber is the absolute line number in the file
   * @param side the side(LEFT or RIGHT) where the comment is added
   */
  public int getPosition(int lineNumber, Side side) {
    return side.equals(Side.LEFT)
        ? lineNumberToPositionLeftSide.get(lineNumber)
        : lineNumberToPositionRightSide.get(lineNumber);
  }

  private void processDiffPatch(String diffPatchStr) {
    GithubPatch githubPatch = new GithubPatch(diffPatchStr);
    List<String> newLineSymbols = githubPatch.getNewlineSymbols();
    // The diff patch can contain one of several parts. We name this part diff hunk.
    List<GithubPatch.DiffHunkHeader> diffHunkHeaders = githubPatch.getDiffHunkHeaders();
    int diffHunkIndex = 0;
    /*
    The line just below the first diff hunk header line is position 1, the next line is position 2, and so on.
    The position in the file's diff continues to increase through lines of whitespace
    and additional hunks until a new file is reached.
     */
    int positionIndex = 1;

    positionToLineNumberLeftSide.put(
        positionIndex, diffHunkHeaders.get(diffHunkIndex).getLeftStartLine());
    positionToLineNumberRightSide.put(
        positionIndex, diffHunkHeaders.get(diffHunkIndex).getRightStartLine());
    lineNumberToPositionLeftSide.put(
        diffHunkHeaders.get(diffHunkIndex).getLeftStartLine(), positionIndex);
    lineNumberToPositionRightSide.put(
        diffHunkHeaders.get(diffHunkIndex).getRightStartLine(), positionIndex);
    positionIndex++;

    for (String n : newLineSymbols) {
      int lastLeftLineNumber =
          positionToLineNumberLeftSide.get(positionToLineNumberLeftSide.size());
      int lastRightLineNumber =
          positionToLineNumberRightSide.get(positionToLineNumberRightSide.size());

      switch (n) {
          /* `\n` - the line with header of diff hunk.
          We should increment the diffHunkIndex, get diff hunk header and
          set correct line number in all maps.
          */
        case "\n":
          {
            ++diffHunkIndex;
            positionToLineNumberLeftSide.put(
                positionIndex, diffHunkHeaders.get(diffHunkIndex).getLeftStartLine());
            positionToLineNumberRightSide.put(
                positionIndex, diffHunkHeaders.get(diffHunkIndex).getRightStartLine());
            lineNumberToPositionLeftSide.put(
                diffHunkHeaders.get(diffHunkIndex).getLeftStartLine(), positionIndex);
            lineNumberToPositionRightSide.put(
                diffHunkHeaders.get(diffHunkIndex).getRightStartLine(), positionIndex);
            positionIndex++;
            break;
          }
          // `\n ` - the line without changes. A comment on this line relates to the left side
        case "\n ":
          {
            positionToLineNumberLeftSide.put(positionIndex, lastLeftLineNumber + 1);
            positionToLineNumberRightSide.put(positionIndex, lastRightLineNumber + 1);
            lineNumberToPositionLeftSide.put(lastLeftLineNumber + 1, positionIndex);
            lineNumberToPositionRightSide.put(lastRightLineNumber + 1, positionIndex);
            positionIndex++;
            break;
          }
          // `\n-` - the deleted line. A comment on this line relates to the left side
        case "\n-":
          {
            positionToLineNumberLeftSide.put(positionIndex, lastLeftLineNumber + 1);
            positionToLineNumberRightSide.put(positionIndex, lastRightLineNumber);
            lineNumberToPositionLeftSide.put(lastLeftLineNumber + 1, positionIndex);
            lineNumberToPositionRightSide.put(lastRightLineNumber, positionIndex);
            positionIndex++;
            break;
          }
          // `\n+` - the added line. A comment on this line relates to the right side
        case "\n+":
          {
            positionToLineNumberLeftSide.put(positionIndex, lastLeftLineNumber);
            positionToLineNumberRightSide.put(positionIndex, lastRightLineNumber + 1);
            lineNumberToPositionLeftSide.put(lastLeftLineNumber, positionIndex);
            lineNumberToPositionRightSide.put(lastRightLineNumber + 1, positionIndex);
            positionIndex++;
            break;
          }
      }
    }
  }
}

