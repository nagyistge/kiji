#!/usr/bin/env python3
# -*- mode: python -*-
# -*- coding: utf-8 -*-
"""Runs the KijiMusic tutorial."""

import glob
import logging
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time

# Add the root directory to the Python path if necessary:
__path = os.path.dirname(os.path.dirname(os.path.abspath(sys.argv[0])))
if __path not in sys.path:
  sys.path.append(__path)

from base import base
from kiji import kiji_bento
from kiji import tutorial_test


FLAGS = base.FLAGS
LogLevel = base.LogLevel


class Error(Exception):
  """Errors used in this module."""
  pass


# Horizontal ruler:
LINE = '-' * 80


# ------------------------------------------------------------------------------


FLAGS.AddString(
  'work_dir',
  help='Working directory.',
)

FLAGS.AddString(
  'maven_local_repo',
  help='Optional Maven local repository from where to fetch artifacts.',
)

FLAGS.AddString(
  'maven_remote_repo',
  help='Optional Maven remote repository from where to fetch artifacts.',
)

FLAGS.AddString(
  'kiji_bento_version',
  default=None,
  help=('Version of KijiBento to download and test against. '
        'For example "1.0.0-rc4" or "1.0.0-rc5-SNAPSHOT". '
        'If not specified, uses the most recent version in the nightly repo.'),
)

FLAGS.AddBoolean(
  'cleanup_after_test',
  default=True,
  help=('When set, disables cleaning up after test. '
        + 'Bento cluster stay alive, working directory is not wiped.'),
)

FLAGS.AddBoolean(
  'help',
  default=False,
  help='Prints a help message.',
)


# ------------------------------------------------------------------------------


class Tutorial(object):
  """Runs the KijiMusic tutorial."""

  def __init__(
      self, work_dir, version,
      maven_local_repo=None,
      maven_remote_repo=None):
    """Initializes the tutorial runner.

    Args:
      work_dir: Working directory where to operate.
      version: Version of KijiBento to test, eg. '1.0.0-rc5-SNAPSHOT'.
      maven_local_repo: Optional local Maven repository.
      maven_remote_repo: Optional remote Maven repository.
    """
    self._work_dir = work_dir
    self._run_id = base.NowMS()
    self._kiji_version = version

    # TODO: inject these in KijiBento
    self._maven_local_repo = maven_local_repo
    self._maven_remote_repo = maven_remote_repo

    # Initialized in Setup()
    self._kiji_bento = kiji_bento.KijiBento(
        path=os.path.join(self.work_dir, 'kiji-bento-%s' % self._kiji_version),
        version=self._kiji_version,
    )

  @property
  def work_dir(self):
    """Returns the working directory."""
    return self._work_dir

  @property
  def kiji_bento(self):
    """Returns the KijiBento install."""
    return self._kiji_bento

  @property
  def bento_cluster(self):
    """Returns the BentoCluster install."""
    return self.kiji_bento.bento_cluster

  def Setup(self):
    """Initializes the tutorial runner.

    Fetches the KijiBento Maven artifact, unzip it, starts a Bento cluster,
    and prepares a working environment.
    """
    self.kiji_bento.Install()
    self.bento_cluster.Start()

    self._kiji_music_dir = (
        os.path.join(self.kiji_bento.path, 'examples', 'music'))
    assert os.path.exists(self._kiji_music_dir), (
        'KijiMusic root directory not found: %r' % self._kiji_music_dir)

    self._hdfs_base = 'kiji-music-%d' % self._run_id
    self._kiji_instance_uri = 'kiji://.env/kiji_music_%d' % self._run_id

    kiji_music_lib_dir = os.path.join(self._kiji_music_dir, 'lib')

    # Builds a working environment for KijiMusic tutorial commands:
    self._env = dict(os.environ)
    self._env.update({
        'MUSIC_HOME': self._kiji_music_dir,
        'LIBS_DIR': kiji_music_lib_dir,
        'KIJI': self._kiji_instance_uri,
        'KIJI_CLASSPATH':
            ':'.join(glob.glob(os.path.join(kiji_music_lib_dir, '*'))),
        'HDFS_BASE': self._hdfs_base,
    })

  def Command(self, command):
    """Runs a Kiji command-line.

    Args:
      command: Kiji command-line to run as a single string.
    """
    cmd = tutorial_test.KijiCommand(
        command=command,
        work_dir=self.kiji_bento.path,
        env=self._env,
    )
    logging.debug('Exit code: %d', cmd.exit_code)
    if logging.getLogger().level <= LogLevel.DEBUG_VERBOSE:
      logging.debug('Output:\n%s\n%s%s', LINE, cmd.output_text, LINE)
      logging.debug('Error:\n%s\n%s%s', LINE, cmd.error_text, LINE)
    else:
      logging.debug('Output: %r', cmd.output_text)
      logging.debug('Error: %r', cmd.error_text)
    return cmd

  # ----------------------------------------------------------------------------
  # KijiMusic setup:

  def Part1(self):
    """Runs the setup part of the KijiMusic tutorial.

    http://docs.kiji.org/tutorials/music-recommendation/1.0.0-rc5/music-setup/
    """

    # --------------------------------------------------------------------------

    install = self.Command('kiji install --kiji=${KIJI}')
    assert (install.exit_code == 0)
    assert ('Successfully created kiji instance: ' in install.output_text)

    # --------------------------------------------------------------------------

    create_table = self.Command("""
        kiji-schema-shell \
            --kiji=${KIJI} \
            --file=${MUSIC_HOME}/music_schema.ddl
    """)
    assert (create_table.exit_code == 0)

    # --------------------------------------------------------------------------

    generate_data = self.Command("""
        rm -f $MUSIC_HOME/example_data/*
        ${MUSIC_HOME}/bin/data_generator.py \
            --output-dir=${MUSIC_HOME}/example_data/
    """)
    assert (generate_data.exit_code == 0)

    # --------------------------------------------------------------------------

    mkdir = self.Command('hadoop fs -mkdir ${HDFS_BASE}/kiji-mr-tutorial/')
    assert (mkdir.exit_code == 0)

    copy = self.Command("""
    hadoop fs -copyFromLocal \
        ${MUSIC_HOME}/example_data/*.json \
        ${HDFS_BASE}/kiji-mr-tutorial/
    """)
    assert (copy.exit_code == 0)

    # --------------------------------------------------------------------------

    list_tables = self.Command('kiji ls ${KIJI}')
    assert (list_tables.exit_code == 0)
    assert ('songs' in list_tables.output_text), (
        'Missing table "songs": %s' % list_tables.output_lines)
    assert ('users' in list_tables.output_text), (
        'Missing table "users": %s' % list_tables.output_lines)


  # ----------------------------------------------------------------------------
  # KijiMusic bulk-importing:

  def Part2(self):
    """Runs the bulk-importing part of the KijiMusic tutorial.

    http://docs.kiji.org/tutorials/music-recommendation/1.0.0-rc5/bulk-importing/
    """

    # --------------------------------------------------------------------------

    bulk_import = self.Command("""
    kiji bulk-import \
        --importer=org.kiji.examples.music.bulkimport.SongMetadataBulkImporter \
        --lib=${LIBS_DIR} \
        --input="format=text \
                 file=${HDFS_BASE}/kiji-mr-tutorial/song-metadata.json" \
        --output="format=kiji \
                  table=${KIJI}/songs \
                  nsplits=1"
    """)
    assert (bulk_import.exit_code == 0)
    # Surprisingly, the bulk-import CLI tools writes nothing to stdout!
    assert ('Total input paths to process : 1' in bulk_import.error_text)
    assert ('BULKIMPORTER_RECORDS_PROCESSED=50' in bulk_import.error_text)

    # --------------------------------------------------------------------------

    list_rows = self.Command('kiji scan ${KIJI}/songs --max-rows=3')
    assert (list_rows.exit_code == 0)

    # --------------------------------------------------------------------------
    # Using table import descriptors:

    copy = self.Command("""
    hadoop fs -copyFromLocal \
      ${MUSIC_HOME}/import/song-plays-import-descriptor.json \
      ${HDFS_BASE}/kiji-mr-tutorial/
    """)
    assert (copy.exit_code == 0)

    # --------------------------------------------------------------------------

    bulk_import = self.Command("""
    kiji bulk-import \
      -Dkiji.import.text.input.descriptor.path=\
${HDFS_BASE}/kiji-mr-tutorial/song-plays-import-descriptor.json \
      --importer=org.kiji.mapreduce.lib.bulkimport.JSONBulkImporter \
      --input="format=text \
               file=${HDFS_BASE}/kiji-mr-tutorial/song-plays.json" \
      --output="format=kiji \
                table=${KIJI}/users \
                nsplits=1" \
      --lib=${LIBS_DIR}
    """)
    assert (bulk_import.exit_code == 0)
    assert ('Total input paths to process : 1' in bulk_import.error_text)
    # Number of records changes from time to time:
    assert('BULKIMPORTER_RECORDS_PROCESSED=' in bulk_import.error_text)

    # --------------------------------------------------------------------------

    list_rows = self.Command('kiji scan ${KIJI}/users --max-rows=3')
    assert (list_rows.exit_code == 0)
    assert (list_rows.output_lines[0].startswith('Scanning kiji table: kiji://'))
    assert (len(list_rows.output_lines) >= 3 * 3 + 1), len(list_rows.output_lines)
    for row in range(0, 3):
      tutorial_test.ExpectRegexMatch(
          expect=r"^entity-id=\['user-\d+'\] \[\d+\] info:track_plays$",
          actual=list_rows.output_lines[1 + row * 3])
      tutorial_test.ExpectRegexMatch(
          expect=r"^\s*song-\d+$",
          actual=list_rows.output_lines[2 + row * 3])
      tutorial_test.ExpectRegexMatch(
          expect=r"^$",
          actual=list_rows.output_lines[3 + row * 3])

  # ----------------------------------------------------------------------------
  # KijiMusic play-count:

  def Part3(self):
    """Runs the play-count part of the KijiMusic tutorial.

    http://docs.kiji.org/tutorials/music-recommendation/1.0.0-rc5/play-count/
    """
    gather = self.Command("""
    kiji gather \
        --gatherer=org.kiji.examples.music.gather.SongPlayCounter \
        --reducer=org.kiji.mapreduce.lib.reduce.LongSumReducer \
        --input="format=kiji table=${KIJI}/users" \
        --output="format=text \
                  file=${HDFS_BASE}/output.txt_file \
                  nsplits=2" \
        --lib=${LIBS_DIR}
    """)
    assert (gather.exit_code == 0)

    # --------------------------------------------------------------------------

    fs_text = self.Command("""
        hadoop fs -text ${HDFS_BASE}/output.txt_file/part-r-00000 | head -3
    """)
    tutorial_test.Expect(expect=0, actual=fs_text.exit_code)
    lines = list(filter(None, fs_text.output_lines))  # filter empty lines
    tutorial_test.Expect(expect=3, actual=len(lines))
    for line in lines:
      tutorial_test.ExpectRegexMatch(expect=r'^song-\d+\t\d+$', actual=line)

  # ----------------------------------------------------------------------------
  # KijiMusic sequential play-count:

  def Part4(self):
    """Runs the sequential play-count part of the KijiMusic tutorial.

    http://docs.kiji.org/tutorials/music-recommendation/1.0.0-rc5/sequential-play-count/
    """
    gather = self.Command("""
    kiji gather \
        --gatherer=org.kiji.examples.music.gather.SequentialPlayCounter \
        --reducer=org.kiji.examples.music.reduce.SequentialPlayCountReducer \
        --input="format=kiji table=${KIJI}/users" \
        --output="format=avrokv \
                  file=${HDFS_BASE}/output.sequentialPlayCount \
                  nsplits=2" \
        --lib=${LIBS_DIR}
    """)
    assert (gather.exit_code == 0)

    # --------------------------------------------------------------------------

    fs_text = self.Command("""
        hadoop fs -text ${HDFS_BASE}/output.txt_file/part-r-00000 | head -3
    """)
    tutorial_test.Expect(expect=0, actual=fs_text.exit_code)
    lines = list(filter(None, fs_text.output_lines))  # filter empty lines
    tutorial_test.Expect(expect=3, actual=len(lines))
    for line in lines:
      tutorial_test.ExpectRegexMatch(expect=r'^song-\d+\t\d+$', actual=line)

  # ----------------------------------------------------------------------------
  # Cleanup:

  def Cleanup(self):
    self.bento_cluster.Stop()
    shutil.rmtree(self.work_dir)


def Main(args):
  """Program entry point."""
  if FLAGS.help:
    FLAGS.PrintUsage()
    return os.EX_OK

  if len(args) > 0:
    logging.error('Unexpected command-line arguments: %r' % args)
    FLAGS.PrintUsage()
    return os.EX_USAGE

  # Create a temporary working directory:
  cwd = os.getcwd()
  work_dir = FLAGS.work_dir
  if work_dir is None:
    work_dir = tempfile.mkdtemp(prefix='work_dir.', dir=os.getcwd())
  work_dir = os.path.abspath(work_dir)
  if not os.path.exists(work_dir):
    os.makedirs(work_dir)
  FLAGS.work_dir = work_dir

  logging.info('Working directory: %r', work_dir)

  logging.info('Testing tutorial of KijiBento %s', FLAGS.kiji_bento_version)

  # Runs the tutorial:
  tutorial = Tutorial(
      work_dir=work_dir,
      version=FLAGS.kiji_bento_version,
      maven_local_repo=FLAGS.maven_local_repo,
      maven_remote_repo=FLAGS.maven_remote_repo,
  )
  tutorial.Setup()
  tutorial.Part1()
  tutorial.Part2()
  tutorial.Part3()
  tutorial.Part4()
  if FLAGS.cleanup_after_test:
    tutorial.Cleanup()


# ------------------------------------------------------------------------------


if __name__ == '__main__':
  base.Run(Main)
