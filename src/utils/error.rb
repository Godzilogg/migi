module Migi
module Error
  def self.message_and_quit(message, optional="")
    puts "\n\nMigi Error: #{message}".red + optional.yellow
    exit
  end
end
end
