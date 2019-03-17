module Migi
class ArgsHelper
  attr_reader \
    :input_filename,
    :input_files_directory,
    :migration_filepath

  # TODO: error when not given a command
  RECOGNIZED_PARAMS = %w(-i -d -m)

  def initialize
    @input_filename = find_arg_value_by_id('-i')
    @input_files_directory = find_arg_value_by_id('-d')
    @migration_filepath = find_arg_value_by_id('-m')

    validate_commands

    Migi::Error.message_and_quit("Nothing to do. No input files were given.") unless any_files_exist?
    Migi::Error.message_and_quit("This is not a directory: #{input_files_directory}") unless (input_filename || directory_exists?)
    Migi::Error.message_and_quit("Migration file not found!") unless migration_exists?
    Migi::Error.message_and_quit("Input file not found!") unless (input_files_directory || input_file_exists?)
  end

  def validate_commands
    ARGV.each_with_index do |arg, index|
      if arg.include?('-')
        unless RECOGNIZED_PARAMS.include?(arg)
          Migi::Error.message_and_quit("Unrecognized command: #{arg}", "\n\nList of valid commands:\n\n#{helper_doc}")
        end
      end
    end
  end

  def helper_doc
    """
    Example usage:
    (-i filename_path | -d directory name) -m migration_filepath

    -i    =   input data file to transform.
    -d    =   input data directory to search for applicable files.
    -m    =   migration file to be applied to data file(s).
    """
  end

  def any_files_exist?
    (input_filename && File.exists?(input_filename)) ||
      (input_files_directory && File.directory?(input_files_directory))
  end

  def directory_exists?
    input_files_directory && File.exists?(input_files_directory)
  end

  def migration_exists?
    migration_filepath && File.exists?(migration_filepath)
  end

  def input_file_exists?
    input_filename && File.exists?(input_filename)
  end

  def find_arg_value_by_id(id = '-i')
    ARGV.each_with_index do |arg, index|
      return(ARGV[index + 1]) if arg.include?(id)
    end

    false
  end

end
end
