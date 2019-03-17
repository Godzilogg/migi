module Migi
module DataMigrator

  def self.migrate_file(data_filepath, migration_filepath)
    # TODO:
    puts 'cool'.magenta
  end

  def self.migrate_directory(directory, migration_filepath)
    list_of_data_files = Dir[directory]
    puts 'cool dir'.magenta
  end

end
end
